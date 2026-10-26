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

import java.io.File;
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
	private static final String K_APP_BG = "apex_app_bg_file";
	private static final String K_WIN_BG = "apex_window_bg_file";
	private static final String K_FONT = "apex_font_file";

	public static final int DEF_TEXT = 0xFFFFFFFF;
	public static final int DEF_OUTLINE = 0xFF0099FF;
	public static final int DEF_BG = 0xFF000000;
	public static final int DEF_PRESSED = 0x660099FF;

	private static Bitmap appBitmap;
	private static boolean appLoaded;
	private static Bitmap winBitmap;
	private static boolean winLoaded;
	private static Typeface typeface;
	private static Typeface boldFace;
	private static boolean fontLoaded;

	private ApexTheme() {
	}

	private static SharedPreferences prefs(Context c) {
		return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
	}

	public static int getColor(Context c, String key, int def) {
		return prefs(c).getInt(key, def);
	}

	public static void setColor(Context c, String key, int color) {
		prefs(c).edit().putInt(key, color).apply();
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

	/** Aplica a fonte do app em todos os textos da hierarquia. */
	public static void applyFontTree(android.view.View v) {
		if (v instanceof android.widget.TextView) {
			applyFont((android.widget.TextView) v);
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
	}

	/** Copia o arquivo escolhido para a pasta interna do app e passa a usa-lo. */
	public static boolean importFile(Context c, int target, Uri uri) {
		File dir = new File(c.getFilesDir(), "theme");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			return false;
		}
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
		clear(c, target);
		prefs(c).edit().putString(keyFor(target), out.getPath()).apply();
		invalidate();
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
	}

	public static void resetAll(Context c) {
		clear(c, TARGET_APP_BG);
		clear(c, TARGET_WINDOW_BG);
		clear(c, TARGET_FONT);
		prefs(c).edit().remove(K_TEXT).remove(K_OUTLINE).remove(K_BG).remove(K_PRESSED).apply();
		invalidate();
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
