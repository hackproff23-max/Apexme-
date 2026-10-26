package ru.woesss.j2me.installer;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Single;

/**
 * Converte/instala varios .jar de uma vez, em sequencia (o dx nao e thread-safe).
 * Reaproveita o AppInstaller normal, so sem os dialogos de confirmacao.
 */
public final class BatchInstaller {
	private static final String TAG = BatchInstaller.class.getSimpleName();

	public interface Listener {
		/** Chamado na thread principal antes de cada jar. index comeca em 1. */
		void onProgress(int index, int total, String fileName);

		/** Chamado na thread principal ao terminar (ou ao cancelar). */
		void onDone(int installed, int skipped, List<String> failed, boolean cancelled);
	}

	private BatchInstaller() {
	}

	public static void start(Application app, ru.playsoftware.j2meloader.appsdb.AppRepository repo,
							 List<File> jars, AtomicBoolean cancel, Listener listener) {
		Handler main = new Handler(Looper.getMainLooper());
		Thread t = new Thread(() -> {
			int installed = 0;
			int skipped = 0;
			List<String> failed = new ArrayList<>();
			int total = jars.size();
			for (int i = 0; i < total; i++) {
				if (cancel.get()) {
					break;
				}
				File jar = jars.get(i);
				final int index = i + 1;
				main.post(() -> listener.onProgress(index, total, jar.getName()));
				AppInstaller installer = new AppInstaller(jar.getAbsolutePath(), Uri.fromFile(jar), app, repo);
				try {
					Integer status = Single.<Integer>create(installer::loadInfo).blockingGet();
					if (status == AppInstaller.STATUS_NEW || status == AppInstaller.STATUS_NEWEST) {
						Integer result = Single.<Integer>create(installer::install).blockingGet();
						if (result == AppInstaller.STATUS_SUCCESS) {
							installed++;
						} else {
							failed.add(jar.getName());
						}
					} else {
						// ja instalado (mesma versao ou mais nova): ignora
						skipped++;
					}
				} catch (Throwable e) {
					Log.e(TAG, "Falha ao converter " + jar, e);
					failed.add(jar.getName());
					try {
						installer.deleteTemp();
					} catch (Throwable ignored) {
					}
				}
			}
			try {
				new AppInstaller(null, Uri.EMPTY, app, repo).clearCache();
			} catch (Throwable ignored) {
			}
			final int fi = installed;
			final int fs = skipped;
			main.post(() -> listener.onDone(fi, fs, failed, cancel.get()));
		}, "Apexme-batch-install");
		t.start();
	}
}
