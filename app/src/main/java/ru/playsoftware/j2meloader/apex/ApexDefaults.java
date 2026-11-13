package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import ru.playsoftware.j2meloader.config.Config;

/**
 * Instala, uma unica vez, os perfis padrao (320 e 480) e o shader SABR v3.0 como shader
 * selecionado. Nao sobrescreve perfis/shaders que ja existam nem uma escolha de shader ja feita.
 */
public final class ApexDefaults {
	private static final String TAG = "ApexDefaults";
	private static final String FLAG_FILE = "apex_defaults";
	private static final String FLAG_KEY = "installed_v1";
	/** Mesmo arquivo de preferencias usado por ApexPrefs. */
	private static final String PREFS_FILE = "apex_extras";
	public static final String SABR = "sabr-v3.0.glsl";

	private ApexDefaults() {
	}

	public static synchronized void install(Context c) {
		try {
			SharedPreferences flag = c.getSharedPreferences(FLAG_FILE, Context.MODE_PRIVATE);
			if (flag.getBoolean(FLAG_KEY, false)) {
				return;
			}
			AssetManager am = c.getAssets();

			// perfis: perfil/320 e perfil/480 (so cria se ainda nao existirem)
			File profiles = new File(Config.getProfilesDir());
			if (!profiles.isDirectory() && !profiles.mkdirs()) {
				return; // pasta ainda nao gravavel: tenta de novo na proxima abertura
			}
			String[] names = am.list("defaults/perfil");
			if (names != null) {
				for (String name : names) {
					File dst = new File(profiles, name);
					if (!dst.exists()) {
						copyAsset(am, "defaults/perfil/" + name, dst);
					}
				}
			}

			// shader SABR v3.0 vira o shader selecionado para todos os perfis/jogos
			File shader = ApexPrefs.userShaderFile(SABR);
			if (!shader.exists()) {
				copyAsset(am, "defaults/shaders/" + SABR, shader);
			}
			SharedPreferences prefs = c.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
			if (shader.isFile() && !prefs.contains("shader_sel")) {
				ApexPrefs.setShaderSel(ApexPrefs.SEL_USER + SABR);
			}

			flag.edit().putBoolean(FLAG_KEY, true).apply();
			ImportStore.sync();
		} catch (Throwable t) {
			Log.e(TAG, "install: ", t);
		}
	}

	private static void copyAsset(AssetManager am, String path, File dst) throws Exception {
		String[] children = am.list(path);
		if (children != null && children.length > 0) {
			//noinspection ResultOfMethodCallIgnored
			dst.mkdirs();
			for (String ch : children) {
				copyAsset(am, path + "/" + ch, new File(dst, ch));
			}
			return;
		}
		File parent = dst.getParentFile();
		if (parent != null && !parent.isDirectory()) {
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		try (InputStream in = am.open(path); OutputStream os = new FileOutputStream(dst)) {
			byte[] buf = new byte[16 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
			}
		}
	}
}
