package javax.microedition.lcdui.keyboard;

import android.graphics.Bitmap;
import android.graphics.RectF;

import javax.microedition.lcdui.graphics.CanvasWrapper;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Desenho comum das teclas extras (letras), com suporte a PNG importado. */
final class ApexKeyPaint {
	private ApexKeyPaint() {
	}

	static void draw(CanvasWrapper g, RectF r, String label, boolean sel, boolean opaque,
					 ProfileModel s, float textScale) {
		int bg = sel ? s.vkBgColorSelected : s.vkBgColor;
		int fg = sel ? s.vkFgColorSelected : s.vkFgColor;
		int a = opaque ? 0xFF : s.vkAlpha;
		Bitmap bmp = ApexPrefs.buttonBitmap(sel);
		if (bmp != null) {
			g.drawBitmap(bmp, r, a);
		} else {
			int corner = (int) (Math.min(r.width(), r.height()) / 4f);
			g.setFillColor((a << 24) | bg);
			g.setDrawColor((a << 24) | s.vkOutlineColor);
			g.fillRoundRect(r, corner, corner);
			g.drawRoundRect(r, corner, corner);
		}
		g.setTextColor((a << 24) | fg);
		g.setTextScale(textScale);
		g.drawString(label, r.centerX(), r.centerY());
		g.setTextScale(1f);
	}
}
