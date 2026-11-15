package javax.microedition.lcdui.keyboard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import ru.playsoftware.j2meloader.apex.ApexControlTheme;
import ru.playsoftware.j2meloader.apex.ApexPrefs;

/**
 * Icones das teclas pretas (brancas no tema claro).
 * A = mover controles (cruz com setas), B = tamanho dos controles (seta diagonal dupla),
 * M = menu (tres linhas), teclado (U+2328) = liga/desliga das letras,
 * 1-9 = direcao (setas; 5 = OK). Os numeros ficam pequenos no canto; A, B e M ficam so com o icone.
 * Retorna false para teclas sem icone: ai vale o desenho normal.
 */
final class XboxIcons {
	/** Rotulo do botao que liga/desliga o teclado de letras. */
	static final String LETTERS_TOGGLE = "\u2328";

	private static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
	private static final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
	private static final Path path = new Path();
	private static final RectF box = new RectF();

	static {
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeJoin(Paint.Join.ROUND);
		tp.setTextAlign(Paint.Align.CENTER);
		tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
	}

	private XboxIcons() {
	}

	private static void head(Canvas c, float tx, float ty, float ux, float uy, float h) {
		double a = Math.atan2(uy, ux);
		c.drawLine(tx, ty, tx + h * (float) Math.cos(a + Math.PI - 0.6), ty + h * (float) Math.sin(a + Math.PI - 0.6), p);
		c.drawLine(tx, ty, tx + h * (float) Math.cos(a + Math.PI + 0.6), ty + h * (float) Math.sin(a + Math.PI + 0.6), p);
	}

	/** Compatibilidade: usa o tema atual do controle. */
	static boolean draw(Canvas c, float cx, float cy, float rad, String label, int tint, boolean onDark) {
		return draw(c, cx, cy, rad, label, tint, onDark, ApexControlTheme.light());
	}

	static boolean draw(Canvas c, float cx, float cy, float rad, String label, int tint, boolean onNeutral,
						boolean light) {
		if (label == null || label.length() != 1 || !onNeutral) return false;
		char ch = label.charAt(0);
		float dx = 0f, dy = 0f;
		int kind;
		switch (ch) {
			case 'A': kind = 1; break;
			case 'B': kind = 2; break;
			case 'M': kind = 7; break;
			case '\u2328': kind = 8; break;
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
		int color = light ? Color.argb(240, 40, 40, 40) : Color.argb(240, 235, 235, 235);
		float s = rad * 0.5f;
		boolean centered = kind == 1 || kind == 2 || kind == 7 || kind == 8;
		float ix = centered ? cx : cx - rad * 0.06f;
		float iy = centered ? cy : cy - rad * 0.1f;
		p.setShader(null);
		p.setColor(color);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(rad * 0.15f);
		switch (kind) {
			case 1: { // mover controles: cruz com 4 setas
				c.drawLine(ix - s, iy, ix + s, iy, p);
				c.drawLine(ix, iy - s, ix, iy + s, p);
				float h = s * 0.5f;
				head(c, ix + s, iy, 1, 0, h);
				head(c, ix - s, iy, -1, 0, h);
				head(c, ix, iy - s, 0, -1, h);
				head(c, ix, iy + s, 0, 1, h);
				break;
			}
			case 2: { // tamanho dos controles: seta diagonal nas duas pontas
				float k = s * 0.8f;
				float h = s * 0.7f;
				c.drawLine(ix - k, iy + k, ix + k, iy - k, p);
				c.drawLine(ix + k, iy - k, ix + k - h, iy - k, p);
				c.drawLine(ix + k, iy - k, ix + k, iy - k + h, p);
				c.drawLine(ix - k, iy + k, ix - k + h, iy + k, p);
				c.drawLine(ix - k, iy + k, ix - k, iy + k - h, p);
				break;
			}
			case 5: // OK / centro
				c.drawCircle(ix, iy, s * 0.85f, p);
				p.setStyle(Paint.Style.FILL);
				c.drawCircle(ix, iy, s * 0.3f, p);
				break;
			case 7: { // menu: tres linhas
				p.setStrokeWidth(rad * 0.16f);
				float w = s * 0.95f;
				float gap = s * 0.62f;
				c.drawLine(ix - w, iy - gap, ix + w, iy - gap, p);
				c.drawLine(ix - w, iy, ix + w, iy, p);
				c.drawLine(ix - w, iy + gap, ix + w, iy + gap, p);
				break;
			}
			case 8: { // teclado de letras: moldura, duas fileiras de teclas e a barra de espaco
				p.setStrokeWidth(rad * 0.11f);
				float w = s * 1.2f;
				float h = s * 0.78f;
				box.set(ix - w, iy - h, ix + w, iy + h);
				c.drawRoundRect(box, s * 0.25f, s * 0.25f, p);
				p.setStyle(Paint.Style.FILL);
				float r = rad * 0.075f;
				for (int i = -1; i <= 1; i++) {
					c.drawCircle(ix + i * s * 0.62f, iy - h * 0.38f, r, p);
					c.drawCircle(ix + i * s * 0.62f, iy + h * 0.02f, r, p);
				}
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(rad * 0.1f);
				c.drawLine(ix - s * 0.55f, iy + h * 0.5f, ix + s * 0.55f, iy + h * 0.5f, p);
				if (!ApexPrefs.lettersEnabled()) { // letras desligadas: risco na diagonal
					p.setStrokeWidth(rad * 0.14f);
					c.drawLine(ix - w * 1.05f, iy + h * 1.2f, ix + w * 1.05f, iy - h * 1.2f, p);
				}
				break;
			}
			default: { // seta de direcao
				float len = (float) Math.hypot(dx, dy);
				float ux = dx / len, uy = dy / len;
				float tx = ix + ux * s, ty = iy + uy * s;
				c.drawLine(ix - ux * s, iy - uy * s, tx, ty, p);
				head(c, tx, ty, ux, uy, s * 0.7f);
			}
		}
		// numero pequeno no canto (so nas teclas numericas; A, B, M e teclado ficam so com o icone)
		if (kind == 5 || kind == 6) {
			tp.setTextSize(rad * 0.36f);
			tp.setColor((color & 0x00FFFFFF) | (0xB0 << 24));
			c.drawText(label, cx + rad * 0.52f, cy + rad * 0.68f, tp);
		}
		return true;
	}
}
