package ru.woesss.j2me.installer;

import android.app.Application;
import android.net.Uri;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.Profile;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.util.FileUtils;

/**
 * Converte varios jogos ao mesmo tempo (2 a 3 por vez), sem janelas por jogo, aplicando o mesmo
 * perfil a todos. Os hashes dos jogos ja instalados sao calculados uma vez so (e guardados em cache).
 */
public final class BatchInstaller {
	public interface Listener {
		void onProgress(int index, int total, String name);

		void onDone(int installed, int skipped, int failed);
	}

	private BatchInstaller() {
	}

	public static void run(Application app, AppRepository repo, List<Uri> uris, Profile profile,
						   Listener listener) {
		new Thread(() -> {
			final int total = uris.size();
			final AtomicInteger ok = new AtomicInteger();
			final AtomicInteger skipped = new AtomicInteger();
			final AtomicInteger failed = new AtomicInteger();
			final AtomicInteger started = new AtomicInteger();
			// jogos ja "reservados" neste lote (mesmo conteudo ou mesmo titulo): o segundo e pulado,
			// como acontecia quando a conversao era um jogo por vez
			final Set<String> claimed = ConcurrentHashMap.newKeySet();

			int cores = Runtime.getRuntime().availableProcessors();
			int workers = Math.max(2, Math.min(3, cores / 2));
			workers = Math.max(1, Math.min(workers, total));
			ExecutorService pool = Executors.newFixedThreadPool(workers);

			GameFingerprint.init(app.getCacheDir());
			GameFingerprint.prewarm(repo, pool);

			for (Uri uri : uris) {
				pool.execute(() -> convertOne(app, repo, uri, profile, listener, total, started,
						claimed, ok, skipped, failed));
			}
			pool.shutdown();
			try {
				pool.awaitTermination(6, TimeUnit.HOURS);
			} catch (InterruptedException e) {
				pool.shutdownNow();
			}
			GameFingerprint.flush();
			try {
				FileUtils.deleteDirectory(new File(app.getCacheDir(), "installer"));
			} catch (Throwable ignored) {
			}
			listener.onDone(ok.get(), skipped.get(), failed.get());
		}).start();
	}

	private static void convertOne(Application app, AppRepository repo, Uri uri, Profile profile,
								   Listener listener, int total, AtomicInteger started, Set<String> claimed,
								   AtomicInteger ok, AtomicInteger skipped, AtomicInteger failed) {
		String name = uri.getLastPathSegment();
		if (name != null && name.contains("/")) {
			name = name.substring(name.lastIndexOf('/') + 1);
		}
		listener.onProgress(started.incrementAndGet(), total, name);
		AppInstaller inst = new AppInstaller(null, uri, app, repo);
		inst.isolate();
		try {
			int status = Single.<Integer>create(inst::loadInfo).blockingGet();
			if (status == AppInstaller.STATUS_NEW || status == AppInstaller.STATUS_NEWEST) {
				if (!claim(inst, claimed)) {
					skipped.incrementAndGet();
				} else {
					int result = Single.<Integer>create(inst::install).blockingGet();
					if (result == AppInstaller.STATUS_SUCCESS) {
						applyProfile(inst.getExistsApp(), profile);
						ok.incrementAndGet();
					} else {
						failed.incrementAndGet();
					}
				}
			} else {
				skipped.incrementAndGet();
			}
		} catch (Throwable e) {
			e.printStackTrace();
			failed.incrementAndGet();
		}
		try {
			inst.clearCache();
			inst.deleteTemp();
		} catch (Throwable ignored) {
		}
		inst.release();
	}

	/** False se outro jogo deste lote ja tem o mesmo conteudo ou o mesmo titulo. */
	private static boolean claim(AppInstaller inst, Set<String> claimed) {
		try {
			String title = GameFingerprint.normalizeTitle(inst.getNewDescriptor().getName());
			String jar = inst.getJar();
			String hash = jar == null ? null : GameFingerprint.hashOf(new File(jar));
			boolean first = true;
			if (hash != null) {
				first = claimed.add("h:" + hash);
			}
			if (!title.isEmpty() && !claimed.add("t:" + title)) {
				first = false;
			}
			return first;
		} catch (Throwable e) {
			return true;
		}
	}

	private static void applyProfile(AppItem app, Profile profile) {
		if (app == null || profile == null) {
			return;
		}
		try {
			File dir = new File(Config.getConfigsDir(), app.getPath());
			//noinspection ResultOfMethodCallIgnored
			dir.mkdirs();
			synchronized (BatchInstaller.class) {
				ProfilesManager.load(profile, dir.getAbsolutePath(), true, true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
