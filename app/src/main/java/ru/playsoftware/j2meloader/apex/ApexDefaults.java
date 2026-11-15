package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.config.Config;

/**
 * Instala, uma unica vez, os perfis padrao (320 e 480) e o shader "4k resolution" (xBRLv Quality)
 * como shader selecionado. A escolha de shader e global (ApexPrefs.shaderSel), entao ele vale para os
 * perfis 320x240, 480 e tambem para jogos sem perfil. Nao sobrescreve perfis/shaders que ja existam
 * nem uma escolha de shader feita pelo usuario; so troca quem ainda esta num padrao antigo (SABR ou J5).
 */
public final class ApexDefaults {
	private static final String TAG = "ApexDefaults";
	private static final String FLAG_FILE = "apex_defaults";
	/** v3: o shader padrao passou de J5PRIME para "4k resolution". Roda uma vez tambem em quem ja tinha a v2. */
	private static final String FLAG_KEY = "installed_v3";
	/** Mesmo arquivo de preferencias usado por ApexPrefs. */
	private static final String PREFS_FILE = "apex_extras";
	/** Shader padrao antigo: continua na pasta shaders/ para quem quiser escolher. */
	public static final String SABR = "sabr-v3.0.glsl";
	/** Shader padrao anterior: continua na pasta shaders/ para quem quiser escolher. */
	public static final String J5 = "J5.glsl";
	/** Shader padrao atual (arquivo unico: vertex + fragment). */
	public static final String K4 = "4k resolution.glsl";
	/** Nome do arquivo dentro de assets/defaults/shaders (sem espaco). */
	private static final String K4_ASSET = "4k_resolution.glsl";

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

			// shaders: 4k resolution (padrao), J5 e SABR (continuam disponiveis na lista)
			File k4 = ApexPrefs.userShaderFile(K4);
			if (!k4.exists()) {
				copyAsset(am, "defaults/shaders/" + K4_ASSET, k4);
			}
			File j5 = ApexPrefs.userShaderFile(J5);
			if (!j5.exists()) {
				copyAsset(am, "defaults/shaders/" + J5, j5);
			}
			File sabr = ApexPrefs.userShaderFile(SABR);
			if (!sabr.exists()) {
				copyAsset(am, "defaults/shaders/" + SABR, sabr);
			}

			// "4k resolution" vira o shader selecionado para todos os perfis/jogos (320, 480 e sem perfil).
			// So troca se ainda nao ha escolha ou se a escolha e um padrao antigo (SABR ou J5).
			SharedPreferences prefs = c.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
			String sel = prefs.getString("shader_sel", null);
			boolean semEscolha = sel == null;
			boolean padraoAntigo = (ApexPrefs.SEL_USER + SABR).equals(sel)
					|| (ApexPrefs.SEL_USER + J5).equals(sel);
			if (k4.isFile() && (semEscolha || padraoAntigo)) {
				ApexPrefs.setShaderSel(ApexPrefs.SEL_USER + K4);
			}

			flag.edit().putBoolean(FLAG_KEY, true).apply();
			ImportStore.sync();
		} catch (Throwable t) {
			Log.e(TAG, "install: ", t);
		}
	}

	/**
	 * Arquivo do shader padrao "4k resolution". Se ele nao estiver na pasta shaders/ (apagado, ou o app
	 * ainda nao rodou o install), copia de novo do asset embutido. Devolve null se nao conseguir.
	 */
	public static synchronized File ensureK4() {
		try {
			File k4 = ApexPrefs.userShaderFile(K4);
			if (k4.isFile()) {
				return k4;
			}
			copyAsset(ContextHolder.getAppContext().getAssets(), "defaults/shaders/" + K4_ASSET, k4);
			return k4.isFile() ? k4 : null;
		} catch (Throwable t) {
			Log.e(TAG, "ensureK4: ", t);
			return null;
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
