package ru.woesss.j2me.installer;

import android.app.Application;
import android.net.Uri;

import java.io.File;
import java.util.List;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.Profile;
import ru.playsoftware.j2meloader.config.ProfilesManager;

/** Converte varios jogos em sequencia, sem janelas por jogo, aplicando o mesmo perfil a todos. */
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
			int ok = 0, skipped = 0, failed = 0;
			int total = uris.size();
			for (int i = 0; i < total; i++) {
				Uri uri = uris.get(i);
				String name = uri.getLastPathSegment();
				if (name != null && name.contains("/")) {
					name = name.substring(name.lastIndexOf('/') + 1);
				}
				listener.onProgress(i + 1, total, name);
				AppInstaller inst = new AppInstaller(null, uri, app, repo);
				try {
					int status = Single.<Integer>create(inst::loadInfo).blockingGet();
					if (status == AppInstaller.STATUS_NEW || status == AppInstaller.STATUS_NEWEST) {
						int result = Single.<Integer>create(inst::install).blockingGet();
						if (result == AppInstaller.STATUS_SUCCESS) {
							applyProfile(inst.getExistsApp(), profile);
							ok++;
						} else {
							failed++;
						}
					} else {
						skipped++;
					}
				} catch (Throwable e) {
					e.printStackTrace();
					failed++;
				}
				try {
					inst.clearCache();
					inst.deleteTemp();
				} catch (Throwable ignored) {
				}
			}
			listener.onDone(ok, skipped, failed);
		}).start();
	}

	private static void applyProfile(AppItem app, Profile profile) {
		if (app == null || profile == null) {
			return;
		}
		try {
			File dir = new File(Config.getConfigsDir(), app.getPath());
			//noinspection ResultOfMethodCallIgnored
			dir.mkdirs();
			ProfilesManager.load(profile, dir.getAbsolutePath(), true, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
