package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

import ru.playsoftware.j2meloader.util.FileUtils;

/**
 * Uma vez: troca o "4k resolution" ja instalado (com brilho 1.05, contraste 1.15 e saturacao 1.17)
 * pela versao neutra do app (tudo 1.0). Se voce ja editou os valores, o arquivo nao e mexido.
 */
final class ApexK4Neutral {
	private static final String KEY = "k4_neutral_v1";

	private ApexK4Neutral() {
	}

	static void run(Context c, SharedPreferences flag) {
		if (flag.getBoolean(KEY, false)) {
			return;
		}
		try {
			File f = ApexPrefs.userShaderFile(ApexDefaults.K4);
			if (f.isFile()) {
				String t = FileUtils.getText(f.getPath());
				if (t != null && has(t, "BRIGHTNESS", "1.05") && has(t, "CONTRAST", "1.15")
						&& has(t, "SATURATION", "1.17")) {
					try (InputStream in = c.getAssets().open("defaults/shaders/4k_resolution.glsl");
						 OutputStream os = new FileOutputStream(f)) {
						byte[] buf = new byte[16 * 1024];
						int n;
						while ((n = in.read(buf)) > 0) {
							os.write(buf, 0, n);
						}
					}
					ImportStore.sync();
				}
			}
			flag.edit().putBoolean(KEY, true).apply();
		} catch (Throwable ignored) {
		}
	}

	private static boolean has(String text, String name, String value) {
		return Pattern.compile("(?m)^#define\\s+" + name + "\\s+" + Pattern.quote(value) + "(?![0-9])")
				.matcher(text).find();
	}
}
