package javax.microedition.lcdui.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;

import javax.microedition.lcdui.graphics.CanvasWrapper;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.apex.ApexPrefs;

/** Imagens padrao (assets/apex_def_*.png). Imagem do usuario vence; sem png volta ao desenho antigo. */
final class ApexDefaults {
	private static final String BUTTON = "apex_def_button.png";
	private static final String DPAD = "apex_def_dpad.png";
	private static final String ANALOG = "apex_def_analog.png";
	private static final HashMap<String, Bitmap> cache = new HashMap<>();
	private static final HashSet<String> missing = new HashSet<>();

	private ApexDefaults() {
	}

	private static synchronized Bitmap load(String name) {
		Bitmap b = cache.get(name);
		if (b != null) return b;
		if (missing.contains(name)) return null;
		try {
			Context c = ContextHolder.getAppContext();
			byte[] data;
			try (InputStream in = c.getAssets().open(name);
				 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
				data = out.toByteArray();
			}
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(data, 0, data.length, o);
			int sample = 1;
			while (o.outWidth / sample > 512 || o.outHeight / sample > 512) sample *= 2;
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = sample;
			b = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
		} catch (Throwable ignored) {
		}
		if (b == null) missing.add(name); else cache.put(name, b);
		return b;
	}

	static Bitmap dpad(Bitmap user) {
		return user != null ? user : load(DPAD);
	}

	/** Mesma imagem para a base e para a bolinha do analogico. */
	static Bitmap analog(Bitmap user) {
		return user != null ? user : load(ANALOG);
	}

	/** Botao normal: imagem padrao + rotulo em branco por cima. */
	static boolean drawKey(CanvasWrapper g, RectF r, String label, boolean pressed) {
		Bitmap b = load(BUTTON);
		if (b == null) return false;
		g.drawBitmap(b, r, Math.round(255 * (pressed ? ApexPrefs.pressedOpacity() : ApexPrefs.normalOpacity())));
		if (label != null && !label.isEmpty()) {
			g.setTextColor(0xFFFFFFFF);
			g.drawString(label, r.centerX(), r.centerY());
		}
		return true;
	}
}
