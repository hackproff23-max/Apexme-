package javax.microedition.lcdui.keyboard;

import android.graphics.RectF;

import java.util.HashMap;

import javax.microedition.lcdui.graphics.CanvasWrapper;

import ru.playsoftware.j2meloader.apex.ApexPrefs;

/** Estilo padrao (Xbox) das teclas, desenhado por codigo. Imagem do usuario continua vencendo. */
final class ApexDefaults {
	private static final HashMap<String, ApexAnim.Value> anims = new HashMap<>();

	private ApexDefaults() {
	}

	/** Botao normal: joia/pilula Xbox com animacao de pressionar. */
	static boolean drawKey(CanvasWrapper g, RectF r, String label, boolean pressed) {
		String id = label + "@" + (int) r.left + ":" + (int) r.top;
		ApexAnim.Value a = anims.get(id);
		if (a == null) {
			a = new ApexAnim.Value();
			anims.put(id, a);
		}
		float t = a.linear(pressed);
		float n = ApexPrefs.normalOpacity();
		float opacity = n + (ApexPrefs.pressedOpacity() - n) * t;
		XboxPainter.INSTANCE.key(g.getCanvas(), r, label, t, opacity);
		return true;
	}
}
