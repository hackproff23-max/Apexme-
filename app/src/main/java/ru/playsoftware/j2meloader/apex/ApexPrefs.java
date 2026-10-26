package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.microedition.util.ContextHolder;

/** Configuracoes extras do Apexme: shaders, analogico, teclado de letras, PNG de botoes. */
public final class ApexPrefs {
	private static final String FILE = "apex_extras";
	public static final String SEL_AUTO = "";
	public static final String SEL_BUILTIN = "builtin";
	public static final String SEL_USER = "user:";

	private static SharedPreferences sp;
	private static SparseIntArray letterMap;
	private static Bitmap btnNormal;
	private static Bitmap btnPressed;
	private static boolean btnLoaded;

	private ApexPrefs() {
	}

	private static synchronized SharedPreferences sp() {
		if (sp == null) {
			sp = ContextHolder.getAppContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
		}
		return sp;
	}

	private static File dir(String sub) {
		File d = new File(ContextHolder.getAppContext().getFilesDir(), "apex/" + sub);
		//noinspection ResultOfMethodCallIgnored
		d.mkdirs();
		return d;
	}

	// ---------------- shaders ----------------
	public static String shaderSel() {
		return sp().getString("shader_sel", SEL_AUTO);
	}

	public static void setShaderSel(String s) {
		sp().edit().putString("shader_sel", s).apply();
	}

	/** Brilho: -0.5 .. 0.5 (padrao 0). */
	public static float brightness() {
		return sp().getFloat("brightness", 0f);
	}

	/** Contraste: 0 .. 2 (padrao 1). */
	public static float contrast() {
		return sp().getFloat("contrast", 1f);
	}

	/** Saturacao: 0 .. 2 (padrao 1). */
	public static float saturation() {
		return sp().getFloat("saturation", 1f);
	}

	/** Opacidade do efeito LCD: 0 .. 1 (padrao 0). */
	public static float lcdOpacity() {
		return sp().getFloat("lcd", 0f);
	}

	public static void setColorValues(float b, float c, float s, float lcd) {
		sp().edit().putFloat("brightness", b).putFloat("contrast", c)
				.putFloat("saturation", s).putFloat("lcd", lcd).apply();
	}

	public static void resetShaderValues() {
		setColorValues(0f, 1f, 1f, 0f);
	}

	public static File userShaderFile(String name) {
		return new File(dir("shaders"), name);
	}

	public static String[] listUserShaders() {
		String[] names = dir("shaders").list();
		if (names == null) {
			return new String[0];
		}
		Arrays.sort(names);
		return names;
	}

	/** Copia um fragment shader (.fsh/.frag/.glsl/.txt) para a pasta do app. Retorna o nome salvo. */
	public static String importShader(Context c, Uri uri) {
		String name = displayName(c, uri);
		if (name == null || name.isEmpty()) {
			name = "shader_" + System.currentTimeMillis() + ".fsh";
		}
		name = name.replaceAll("[^A-Za-z0-9._-]", "_");
		File out = userShaderFile(name);
		try (InputStream in = c.getContentResolver().openInputStream(uri);
			 OutputStream os = new FileOutputStream(out)) {
			if (in == null) {
				return null;
			}
			byte[] buf = new byte[8192];
			int n;
			long total = 0;
			while ((n = in.read(buf)) > 0) {
				total += n;
				if (total > 512 * 1024) {
					throw new java.io.IOException("too big");
				}
				os.write(buf, 0, n);
			}
		} catch (Exception e) {
			//noinspection ResultOfMethodCallIgnored
			out.delete();
			return null;
		}
		String text = ru.playsoftware.j2meloader.util.FileUtils.getText(out.getPath());
		if (!text.contains("main")) {
			//noinspection ResultOfMethodCallIgnored
			out.delete();
			return null;
		}
		return name;
	}

	private static String displayName(Context c, Uri uri) {
		try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
			if (cur != null && cur.moveToFirst()) {
				int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (i >= 0) {
					return cur.getString(i);
				}
			}
		} catch (Exception ignored) {
		}
		return uri.getLastPathSegment();
	}

	// ---------------- teclado virtual ----------------
	/** Escala global dos botoes: 0.5 .. 2 (padrao 1). */
	public static float buttonScale() {
		return sp().getFloat("btn_scale", 1f);
	}

	public static void setButtonScale(float v) {
		sp().edit().putFloat("btn_scale", v).apply();
	}

	public static boolean analogEnabled() {
		return sp().getBoolean("analog_on", true);
	}

	public static void setAnalogEnabled(boolean v) {
		sp().edit().putBoolean("analog_on", v).apply();
	}

	/** Escala do analogico: 0.5 .. 2 (padrao 1). */
	public static float analogScale() {
		return sp().getFloat("analog_scale", 1f);
	}

	public static void setAnalogScale(float v) {
		sp().edit().putFloat("analog_scale", v).apply();
	}

	public static boolean lettersEnabled() {
		return sp().getBoolean("letters_on", false);
	}

	public static void setLettersEnabled(boolean v) {
		sp().edit().putBoolean("letters_on", v).apply();
	}

	public static float[] pos(String name, boolean landscape, float defX, float defY) {
		String k = name + (landscape ? "_l" : "_p");
		return new float[]{sp().getFloat(k + "x", defX), sp().getFloat(k + "y", defY)};
	}

	public static void setPos(String name, boolean landscape, float x, float y) {
		String k = name + (landscape ? "_l" : "_p");
		sp().edit().putFloat(k + "x", x).putFloat(k + "y", y).apply();
	}

	// ---------------- mapeamento letra -> numero ----------------
	private static synchronized SparseIntArray letters() {
		if (letterMap == null) {
			letterMap = new SparseIntArray();
			String s = sp().getString("letter_map", "");
			for (String e : s.split(";")) {
				String[] kv = e.split(":");
				if (kv.length == 2) {
					try {
						letterMap.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return letterMap;
	}

	/** Codigo (MIDP) para o qual a letra esta mapeada, ou -1. letter = 'a'..'z'. */
	public static int letterTarget(char letter) {
		return letters().get(letter, -1);
	}

	public static synchronized void setLetterMap(SparseIntArray map) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < map.size(); i++) {
			sb.append(map.keyAt(i)).append(':').append(map.valueAt(i)).append(';');
		}
		letterMap = map.clone();
		sp().edit().putString("letter_map", sb.toString()).apply();
	}

	/** Se o codigo for uma letra mapeada, devolve o numero correspondente; senao o proprio codigo. */
	public static int remapLetter(int code) {
		int c = (code >= 'A' && code <= 'Z') ? code + 32 : code;
		if (c < 'a' || c > 'z') {
			return code;
		}
		return letters().get(c, code);
	}

	// ---------------- PNG dos botoes ----------------
	public static synchronized Bitmap buttonBitmap(boolean pressed) {
		if (!btnLoaded) {
			btnNormal = decode(new File(dir("buttons"), "normal.png"));
			btnPressed = decode(new File(dir("buttons"), "pressed.png"));
			btnLoaded = true;
		}
		return pressed && btnPressed != null ? btnPressed : btnNormal;
	}

	private static Bitmap decode(File f) {
		if (!f.exists()) {
			return null;
		}
		try {
			return BitmapFactory.decodeFile(f.getPath());
		} catch (Throwable e) {
			return null;
		}
	}

	public static synchronized boolean importButtonPng(Context c, Uri uri, boolean pressed) {
		File out = new File(dir("buttons"), pressed ? "pressed.png" : "normal.png");
		File tmp = new File(out.getPath() + ".tmp");
		try (InputStream in = c.getContentResolver().openInputStream(uri);
			 OutputStream os = new FileOutputStream(tmp)) {
			if (in == null) {
				return false;
			}
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
			}
		} catch (Exception e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return false;
		}
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(tmp.getPath(), o);
		if (o.outWidth <= 0 || o.outHeight <= 0) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return false;
		}
		//noinspection ResultOfMethodCallIgnored
		out.delete();
		boolean ok = tmp.renameTo(out);
		btnLoaded = false;
		return ok;
	}

	public static synchronized void clearButtonPngs() {
		File d = dir("buttons");
		File[] files = d.listFiles();
		if (files != null) {
			for (File f : files) {
				//noinspection ResultOfMethodCallIgnored
				f.delete();
			}
		}
		btnLoaded = false;
	}

	public static boolean hasButtonPng() {
		return buttonBitmap(false) != null;
	}
}
