package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.DisplayMetrics;

import androidx.preference.PreferenceManager;

import ru.playsoftware.j2meloader.config.Config;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Configuracoes de aparencia do Apexme (cores, fundos e fonte importados). */
public final class ApexTheme {
	public static final int TARGET_APP_BG = 0;
	public static final int TARGET_WINDOW_BG = 1;
	public static final int TARGET_FONT = 2;

	public static final String K_TEXT = "apex_text_color";
	public static final String K_OUTLINE = "apex_outline_color";
	public static final String K_BG = "apex_bg_color";
	public static final String K_PRESSED = "apex_pressed_color";
	public static final String K_WINDOW = "apex_window_color";
	/** Espessura do contorno em porcentagem (0 = sem contorno, 100 = padrao). */
	public static final String K_OUTLINE_WIDTH = "apex_outline_width";
	public static final int DEF_OUTLINE_WIDTH = 100;
	private static final String K_APP_BG = "apex_app_bg_file";
	private static final String K_WIN_BG = "apex_window_bg_file";
	private static final String K_FONT = "apex_font_file";

	public static final int DEF_TEXT = 0xFFE6E6E6;
	public static final int DEF_OUTLINE = 0xFFF2A900;
	public static final int DEF_BG = 0xFF000000;
	public static final int DEF_PRESSED = 0x66F2A900;

	private static final String SETTINGS_FILE = "tema.json";
	private static final String[] COLOR_KEYS = {K_TEXT, K_OUTLINE, K_BG, K_PRESSED, K_WINDOW, K_OUTLINE_WIDTH};
	private static final String[] FILE_KEYS = {K_APP_BG, K_WIN_BG, K_FONT};

	private static Bitmap appBitmap;
	private static boolean appLoaded;
	private static Bitmap winBitmap;
	private static boolean winLoaded;
	private static Typeface typeface;
	private static Typeface boldFace;
	private static int version;
	private static boolean fontLoaded;

	private ApexTheme() {
	}

	private static SharedPreferences prefs(Context c) {
		return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
	}

	public static int getColor(Context c, String key, int def) {
		return prefs(c).getInt(key, def);
	}

	/** Muda a cada alteracao de cor, fundo ou fonte; as telas comparam para se recriar. */
	public static synchronized int version() {
		return version;
	}

	public static void setColor(Context c, String key, int color) {
		prefs(c).edit().putInt(key, color).apply();
		synchronized (ApexTheme.class) {
			version++;
		}
		syncToFile(c);
	}

	private static float scaleCache = 1f;
	private static int scaleVersion = -1;

	/** Fator da espessura do contorno (1 = padrao, 0 = sem contorno). */
	public static float outlineScale(Context c) {
		synchronized (ApexTheme.class) {
			if (scaleVersion != version) {
				scaleCache = Math.max(0, getColor(c, K_OUTLINE_WIDTH, DEF_OUTLINE_WIDTH)) / 100f;
				scaleVersion = version;
			}
			return scaleCache;
		}
	}

	public static int textColor(Context c) {
		return getColor(c, K_TEXT, DEF_TEXT);
	}

	public static int outlineColor(Context c) {
		return getColor(c, K_OUTLINE, DEF_OUTLINE);
	}

	public static int bgColor(Context c) {
		return getColor(c, K_BG, DEF_BG);
	}

	public static int windowColor(Context c) {
		return getColor(c, K_WINDOW, DEF_BG);
	}

	public static int pressedColor(Context c) {
		return getColor(c, K_PRESSED, DEF_PRESSED);
	}

	public static synchronized Bitmap appBitmap(Context c) {
		if (!appLoaded) {
			appBitmap = loadBitmap(c, K_APP_BG);
			appLoaded = true;
		}
		return appBitmap;
	}

	public static synchronized Bitmap windowBitmap(Context c) {
		if (!winLoaded) {
			winBitmap = loadBitmap(c, K_WIN_BG);
			winLoaded = true;
		}
		return winBitmap;
	}

	public static synchronized Typeface typeface(Context c) {
		if (!fontLoaded) {
			typeface = null;
			String path = prefs(c).getString(K_FONT, null);
			if (path != null && new File(path).exists()) {
				try {
					Typeface t = Typeface.createFromFile(path);
					if (t != null && t != Typeface.DEFAULT) {
						typeface = t;
					}
				} catch (Exception ignored) {
				}
			}
			fontLoaded = true;
		}
		return typeface;
	}

	/** Fonte do app (importada ou padrao do sistema) sempre em negrito, nunca italico. */
	public static synchronized Typeface boldTypeface(Context c) {
		if (boldFace == null) {
			Typeface t = typeface(c);
			boldFace = Typeface.create(t != null ? t : Typeface.DEFAULT, Typeface.BOLD);
		}
		return boldFace;
	}

	/** Aplica a fonte do app (negrito, sem italico) neste texto. */
	public static void applyFont(android.widget.TextView tv) {
		Typeface t = boldTypeface(tv.getContext());
		if (tv.getTypeface() != t) {
			tv.setTypeface(t, Typeface.BOLD);
		}
	}

	private static final java.util.Map<android.widget.TextView, Boolean> OUTLINED =
			new java.util.WeakHashMap<>();

	/** Garante contorno em textos que nao sao OutlinedTextView (titulo da barra, menus, botoes, switches...). */
	public static void applyOutline(android.widget.TextView tv) {
		if (tv instanceof ru.playsoftware.j2meloader.applist.OutlinedTextView || OUTLINED.containsKey(tv)) {
			return;
		}
		OUTLINED.put(tv, Boolean.TRUE);
		final int color = outlineColor(tv.getContext());
		tv.getOverlay().add(new android.graphics.drawable.Drawable() {
			@Override
			public void draw(Canvas canvas) {
				if (tv.getText() != null && tv.getText().length() > 0) {
					ru.playsoftware.j2meloader.applist.OutlinedTextView.drawOutline(tv, canvas, color, true);
				}
			}

			@Override
			public void setAlpha(int alpha) {
			}

			@Override
			public void setColorFilter(android.graphics.ColorFilter colorFilter) {
			}

			@Override
			public int getOpacity() {
				return android.graphics.PixelFormat.TRANSLUCENT;
			}
		});
		tv.invalidate();
	}

	/** Aplica a fonte do app em todos os textos da hierarquia. */
	public static void applyFontTree(android.view.View v) {
		if (v instanceof android.widget.TextView) {
			android.widget.TextView tv = (android.widget.TextView) v;
			applyFont(tv);
			applyOutline(tv);
		}
		if (v instanceof android.view.ViewGroup) {
			android.view.ViewGroup g = (android.view.ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				applyFontTree(g.getChildAt(i));
			}
		}
	}

	private static Bitmap loadBitmap(Context c, String key) {
		String path = prefs(c).getString(key, null);
		if (path == null || !new File(path).exists()) {
			return null;
		}
		try {
			DisplayMetrics dm = c.getResources().getDisplayMetrics();
			int max = Math.max(dm.widthPixels, dm.heightPixels) * 2;
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(path, o);
			int sample = 1;
			while (o.outWidth / (sample * 2) >= max || o.outHeight / (sample * 2) >= max) {
				sample *= 2;
			}
			o = new BitmapFactory.Options();
			o.inSampleSize = sample;
			return BitmapFactory.decodeFile(path, o);
		} catch (Throwable e) {
			return null;
		}
	}

	private static String keyFor(int target) {
		switch (target) {
			case TARGET_APP_BG:
				return K_APP_BG;
			case TARGET_WINDOW_BG:
				return K_WIN_BG;
			default:
				return K_FONT;
		}
	}

	private static synchronized void invalidate() {
		appLoaded = false;
		winLoaded = false;
		fontLoaded = false;
		appBitmap = null;
		winBitmap = null;
		typeface = null;
		boldFace = null;
		version++;
	}

	/** Copia o arquivo escolhido para a pasta interna do app e passa a usa-lo. */
	public static boolean importFile(Context c, int target, Uri uri) {
		File dir = ru.playsoftware.j2meloader.apex.ImportStore.dir(
				target == TARGET_FONT ? ru.playsoftware.j2meloader.apex.ImportStore.FONTES
						: ru.playsoftware.j2meloader.apex.ImportStore.FUNDOS);
		File out = new File(dir, target == TARGET_FONT ? "font_" + System.currentTimeMillis() + fontExt(c, uri)
				: (target == TARGET_APP_BG ? "app_bg_" : "window_bg_") + System.currentTimeMillis());
		try (InputStream in = c.getContentResolver().openInputStream(uri);
			 OutputStream os = new FileOutputStream(out)) {
			if (in == null) {
				return false;
			}
			byte[] buf = new byte[16 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
			}
		} catch (Exception e) {
			//noinspection ResultOfMethodCallIgnored
			out.delete();
			return false;
		}
		boolean valid;
		if (target == TARGET_FONT) {
			try {
				Typeface t = Typeface.createFromFile(out);
				valid = t != null && t != Typeface.DEFAULT;
			} catch (Exception e) {
				valid = false;
			}
		} else {
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(out.getPath(), o);
			valid = o.outWidth > 0 && o.outHeight > 0;
		}
		if (!valid) {
			//noinspection ResultOfMethodCallIgnored
			out.delete();
			return false;
		}
		if (target != TARGET_FONT) {
			// da a extensao certa ao arquivo (png, jpg)
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(out.getPath(), o2);
			String ext = "image/png".equals(o2.outMimeType) ? ".png"
					: "image/jpeg".equals(o2.outMimeType) ? ".jpg" : ".img";
			File renamed = new File(dir, out.getName() + ext);
			if (out.renameTo(renamed)) {
				out = renamed;
			}
		}
		clear(c, target);
		prefs(c).edit().putString(keyFor(target), out.getPath()).apply();
		invalidate();
		syncToFile(c);
		ru.playsoftware.j2meloader.apex.ImportStore.sync();
		return true;
	}

	private static String fontExt(Context c, Uri uri) {
		String name = null;
		try (android.database.Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
			if (cur != null && cur.moveToFirst()) {
				int i = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
				if (i >= 0) {
					name = cur.getString(i);
				}
			}
		} catch (Exception ignored) {
		}
		return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".otf") ? ".otf" : ".ttf";
	}

	public static void clear(Context c, int target) {
		String old = prefs(c).getString(keyFor(target), null);
		if (old != null) {
			//noinspection ResultOfMethodCallIgnored
			new File(old).delete();
		}
		prefs(c).edit().remove(keyFor(target)).apply();
		invalidate();
		syncToFile(c);
		ru.playsoftware.j2meloader.apex.ImportStore.sync();
	}

	/** Subpasta de importacao/ onde fica o arquivo desta chave. */
	private static String subFor(String key) {
		return K_FONT.equals(key) ? ru.playsoftware.j2meloader.apex.ImportStore.FONTES
				: ru.playsoftware.j2meloader.apex.ImportStore.FUNDOS;
	}

	/** Nome do arquivo importado em uso para este alvo, ou null. */
	public static String inUseName(Context c, int target) {
		String path = prefs(c).getString(keyFor(target), null);
		return path != null ? new File(path).getName() : null;
	}

	public static void resetAll(Context c) {
		clear(c, TARGET_APP_BG);
		clear(c, TARGET_WINDOW_BG);
		clear(c, TARGET_FONT);
		prefs(c).edit().remove(K_TEXT).remove(K_OUTLINE).remove(K_BG).remove(K_WINDOW).remove(K_PRESSED).remove(K_OUTLINE_WIDTH).apply();
		invalidate();
		syncToFile(c);
	}

	/** Grava as cores e os nomes dos arquivos importados em configuracoes/tema.json. */
	public static void syncToFile(Context c) {
		try {
			SharedPreferences p = prefs(c);
			JSONObject o = new JSONObject();
			for (String k : COLOR_KEYS) {
				if (p.contains(k)) {
					o.put(k, p.getInt(k, 0));
				}
			}
			for (String k : FILE_KEYS) {
				String path = p.getString(k, null);
				if (path != null) {
					o.put(k, new File(path).getName());
				}
			}
			File dir = new File(Config.getSettingsDir());
			if (!dir.isDirectory() && !dir.mkdirs()) {
				return;
			}
			try (FileOutputStream os = new FileOutputStream(new File(dir, SETTINGS_FILE))) {
				os.write(o.toString(2).getBytes("UTF-8"));
			}
		} catch (Throwable ignored) {
		}
	}

	/**
	 * Chamado quando as pastas ficam prontas: leva arquivos importados antigos para importacao/
	 * e carrega o tema salvo em configuracoes/tema.json (ele vale sobre as preferencias).
	 */
	public static void restoreFromFile(Context c) {
		try {
			SharedPreferences p = prefs(c);
			File imp = new File(Config.getImportDir());
			if (!imp.isDirectory() && !imp.mkdirs()) {
				return;
			}
			// 1. arquivos importados em versoes anteriores (pasta interna) -> importacao/
			SharedPreferences.Editor e1 = p.edit();
			boolean moved = false;
			for (String k : FILE_KEYS) {
				String path = p.getString(k, null);
				if (path == null) {
					continue;
				}
				File sub = ru.playsoftware.j2meloader.apex.ImportStore.dir(subFor(k));
				File f = new File(path);
				if (f.exists() && !sub.equals(f.getParentFile())) {
					File dst = new File(sub, f.getName());
					if (copyFile(f, dst)) {
						//noinspection ResultOfMethodCallIgnored
						f.delete();
						e1.putString(k, dst.getPath());
						moved = true;
					}
				}
			}
			if (moved) {
				e1.apply();
			}
			// 2. tema salvo em arquivo
			File settings = new File(Config.getSettingsDir(), SETTINGS_FILE);
			if (!settings.isFile()) {
				syncToFile(c);
				if (moved) {
					invalidate();
				}
				return;
			}
			JSONObject o = new JSONObject(readText(settings));
			SharedPreferences.Editor e = p.edit();
			boolean changed = moved;
			for (String k : COLOR_KEYS) {
				if (o.has(k)) {
					int v = o.getInt(k);
					if (!p.contains(k) || p.getInt(k, 0) != v) {
						e.putInt(k, v);
						changed = true;
					}
				} else if (p.contains(k)) {
					e.remove(k);
					changed = true;
				}
			}
			for (String k : FILE_KEYS) {
				String want = null;
				if (o.has(k)) {
					File f = new File(ru.playsoftware.j2meloader.apex.ImportStore.dir(subFor(k)), o.getString(k));
					if (!f.exists()) {
						f = new File(imp, o.getString(k));
					}
					if (f.exists()) {
						want = f.getPath();
					}
				}
				String cur = p.getString(k, null);
				if (want == null ? cur != null : !want.equals(cur)) {
					if (want == null) {
						e.remove(k);
					} else {
						e.putString(k, want);
					}
					changed = true;
				}
			}
			if (changed) {
				e.apply();
				invalidate();
			}
		} catch (Throwable ignored) {
		}
	}

	private static boolean copyFile(File src, File dst) {
		try (InputStream in = new FileInputStream(src); OutputStream os = new FileOutputStream(dst)) {
			byte[] buf = new byte[16 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static String readText(File f) throws java.io.IOException {
		try (InputStream in = new FileInputStream(f)) {
			java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) > 0) {
				bos.write(buf, 0, n);
			}
			return bos.toString("UTF-8");
		}
	}

	public static boolean hasAppImage(Context c) {
		return appBitmap(c) != null;
	}

	/** Desenha o bitmap preenchendo dst (center crop). */
	public static void drawCenterCrop(Canvas canvas, Bitmap bmp, Rect dst, Paint paint) {
		int dw = dst.width();
		int dh = dst.height();
		if (dw <= 0 || dh <= 0) {
			return;
		}
		float scale = Math.max((float) dw / bmp.getWidth(), (float) dh / bmp.getHeight());
		int sw = Math.min(bmp.getWidth(), Math.round(dw / scale));
		int sh = Math.min(bmp.getHeight(), Math.round(dh / scale));
		int left = (bmp.getWidth() - sw) / 2;
		int top = (bmp.getHeight() - sh) / 2;
		canvas.drawBitmap(bmp, new Rect(left, top, left + sw, top + sh), dst, paint);
	}
}
