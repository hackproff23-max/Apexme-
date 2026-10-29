package javax.microedition.lcdui.keyboard;

import android.graphics.Bitmap;
import android.graphics.RectF;

import javax.microedition.lcdui.graphics.CanvasWrapper;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Desenho comum das teclas extras (letras), com imagem propria por tecla. */
final class ApexKeyPaint {
	private ApexKeyPaint() {
	}

	static void draw(CanvasWrapper g, RectF r, String id, String label, boolean sel, boolean opaque,
					 ProfileModel s, float textScale) {
		int bg = sel ? s.vkBgColorSelected : s.vkBgColor;
		int fg = sel ? s.vkFgColorSelected : s.vkFgColor;
		int a = opaque ? 0xFF : s.vkAlpha;
		Bitmap bmp = ApexPrefs.buttonBitmap(id, sel);
		if (bmp != null) {
			// com imagem: so a imagem, sem a letra da tecla por cima
			g.drawBitmap(bmp, r, a);
			return;
		}
		int corner = (int) (Math.min(r.width(), r.height()) / 4f);
		g.setFillColor((a << 24) | bg);
		g.setDrawColor((a << 24) | s.vkOutlineColor);
		g.fillRoundRect(r, corner, corner);
		g.drawRoundRect(r, corner, corner);
		g.setTextColor((a << 24) | fg);
		g.setTextScale(textScale);
		g.drawString(label, r.centerX(), r.centerY());
		g.setTextScale(1f);
	}
}
