package javax.microedition.lcdui.keyboard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

/**
 * Icones das teclas: cada tecla mostra o que ela representa, independente do jogo.
 * A = confirmar (check), B = cancelar (X), C/X = acao (raio), D/Y = menu (3 linhas),
 * 1-9 = direcao (setas; 5 = OK). A letra/numero fica pequeno no canto.
 * Retorna false para teclas sem icone (0, *, #, etc.): ai vale o desenho antigo.
 */
final class XboxIcons {
	private static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
	private static final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
	private static final Path path = new Path();

	static {
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeJoin(Paint.Join.ROUND);
		tp.setTextAlign(Paint.Align.CENTER);
		tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
	}

	private XboxIcons() {
	}

	static boolean draw(Canvas c, float cx, float cy, float rad, String label, int tint, boolean onDark) {
		if (label == null || label.length() != 1) return false;
		char ch = label.charAt(0);
		float dx = 0f, dy = 0f;
		int kind;
		switch (ch) {
			case 'A': kind = 1; break;
			case 'B': kind = 2; break;
			case 'C': case 'X': kind = 3; break;
			case 'D': case 'Y': kind = 4; break;
			case '5': kind = 5; break;
			case '1': kind = 6; dx = -1; dy = -1; break;
			case '2': kind = 6; dy = -1; break;
			case '3': kind = 6; dx = 1; dy = -1; break;
			case '4': kind = 6; dx = -1; break;
			case '6': kind = 6; dx = 1; break;
			case '7': kind = 6; dx = -1; dy = 1; break;
			case '8': kind = 6; dy = 1; break;
			case '9': kind = 6; dx = 1; dy = 1; break;
			default: return false;
		}
		int color = onDark ? Color.argb(240, 235, 235, 235) : ((tint & 0x00FFFFFF) | (0xE6 << 24));
		float s = rad * 0.5f;
		float ix = cx - rad * 0.06f, iy = cy - rad * 0.1f;
		p.setShader(null);
		p.setColor(color);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(rad * 0.17f);
		path.reset();
		switch (kind) {
			case 1: // confirmar
				path.moveTo(ix - s * 0.9f, iy);
				path.lineTo(ix - s * 0.25f, iy + s * 0.7f);
				path.lineTo(ix + s * 0.95f, iy - s * 0.7f);
				c.drawPath(path, p);
				break;
			case 2: // cancelar
				c.drawLine(ix - s * 0.75f, iy - s * 0.75f, ix + s * 0.75f, iy + s * 0.75f, p);
				c.drawLine(ix + s * 0.75f, iy - s * 0.75f, ix - s * 0.75f, iy + s * 0.75f, p);
				break;
			case 3: // acao (raio)
				path.moveTo(ix + s * 0.3f, iy - s);
				path.lineTo(ix - s * 0.55f, iy + s * 0.1f);
				path.lineTo(ix + s * 0.5f, iy + s * 0.1f);
				path.lineTo(ix - s * 0.3f, iy + s);
				c.drawPath(path, p);
				break;
			case 4: // menu
				for (int i = -1; i <= 1; i++) {
					c.drawLine(ix - s * 0.85f, iy + i * s * 0.7f, ix + s * 0.85f, iy + i * s * 0.7f, p);
				}
				break;
			case 5: // OK / centro
				c.drawCircle(ix, iy, s * 0.85f, p);
				p.setStyle(Paint.Style.FILL);
				c.drawCircle(ix, iy, s * 0.3f, p);
				break;
			default: { // seta de direcao
				float len = (float) Math.hypot(dx, dy);
				float ux = dx / len, uy = dy / len;
				float tx = ix + ux * s, ty = iy + uy * s;
				c.drawLine(ix - ux * s, iy - uy * s, tx, ty, p);
				double a = Math.atan2(uy, ux);
				float h = s * 0.7f;
				c.drawLine(tx, ty, tx + h * (float) Math.cos(a + Math.PI - 0.6), ty + h * (float) Math.sin(a + Math.PI - 0.6), p);
				c.drawLine(tx, ty, tx + h * (float) Math.cos(a + Math.PI + 0.6), ty + h * (float) Math.sin(a + Math.PI + 0.6), p);
			}
		}
		// letra/numero pequeno no canto
		tp.setTextSize(rad * 0.36f);
		tp.setColor((color & 0x00FFFFFF) | (0xB0 << 24));
		c.drawText(label, cx + rad * 0.52f, cy + rad * 0.68f, tp);
		return true;
	}
}
