package javax.microedition.lcdui.keyboard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Desenha os controles no estilo do controle Xbox original (porte do ControlPainter.kt).
 * Todos os metodos recebem "t" (0..1): quanto o controle esta pressionado, para animar.
 * Nao e thread-safe: use sempre na thread de desenho.
 */
final class XboxPainter {
	static final XboxPainter INSTANCE = new XboxPainter();

	static final int DPAD_UP = 1, DPAD_DOWN = 2, DPAD_LEFT = 4, DPAD_RIGHT = 8;
	private static final int WELL_FILL = 45;

	private static final class Jewel {
		final int light, base, dark;

		Jewel(int light, int base, int dark) {
			this.light = light;
			this.base = base;
			this.dark = dark;
		}
	}

	private static final Jewel GREEN = new Jewel(0xFF9BE07F, 0xFF3E9F32, 0xFF164D12);
	private static final Jewel RED = new Jewel(0xFFFF8A7A, 0xFFD12A22, 0xFF5E0C08);
	private static final Jewel BLUE = new Jewel(0xFF8CC4FF, 0xFF1F6FD0, 0xFF0A2F66);
	private static final Jewel YELLOW = new Jewel(0xFFFFEE8F, 0xFFF2BE12, 0xFF7A5A00);
	private static final Jewel DARK = new Jewel(0xFF6E6E6E, 0xFF2E2E2E, 0xFF0C0C0C);

	private static final float[][] AXES = {{0f, -1f}, {0f, 1f}, {-1f, 0f}, {1f, 0f}};
	private static final int[] DIR_BITS = {DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT};

	private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	private final Path path = new Path();
	private final Path bar = new Path();

	private XboxPainter() {
		text.setTextAlign(Paint.Align.CENTER);
		text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
	}

	// ------------------------------------------------------------ API

	/** Tecla do teclado virtual. Retangulo largo vira pilula; o resto, joia redonda. */
	void key(Canvas c, RectF r, String label, float t, float opacity) {
		float w = r.width(), h = r.height();
		float cx = r.centerX(), cy = r.centerY();
		int save = begin(c, cx, cy, Math.max(w, h) * 0.65f, opacity);
		if (w > h * 1.45f) {
			pill(c, r, label, t);
		} else {
			float rad = Math.min(w, h) / 2f / 1.13f;
			Jewel j = jewelFor(label);
			socket(c, cx, cy, rad * 1.13f, 150);
			jewel(c, cx, cy, rad, j, t);
			if (XboxIcons.draw(c, cx, cy, rad, label, j.dark, j == DARK)) {
				c.restoreToCount(save);
				return;
			}
			if (label != null && !label.isEmpty()) {
				if (j == DARK) {
					text.setColor(Color.argb(230, 235, 235, 235));
					fit(label, rad * 0.8f, rad * 1.5f);
					c.drawText(label, cx, cy + text.getTextSize() * 0.35f, text);
				} else {
					fit(label, rad * 0.95f, rad * 1.5f);
					float base = cy + text.getTextSize() * 0.35f;
					text.setColor(Color.argb(90, 255, 255, 255));
					c.drawText(label, cx, base + rad * 0.05f, text);
					text.setColor(withAlpha(j.dark, 215));
					c.drawText(label, cx, base, text);
				}
			}
		}
		c.restoreToCount(save);
	}

	/**
	 * D-pad: cruz preta que "balanca". bits = DPAD_* das direcoes seguradas
	 * (mantenha o ultimo valor enquanto t volta a 0, para o fade-out).
	 */
	void dpad(Canvas c, float cx, float cy, float r, int bits, float t, float opacity) {
		int save = begin(c, cx, cy, r * 1.2f, opacity);
		socket(c, cx, cy, r, WELL_FILL);

		float aw = r * 0.29f;
		float al = r * 0.84f;
		path.reset();
		path.addRoundRect(cx - aw, cy - al, cx + aw, cy + al, aw * 0.85f, aw * 0.85f, Path.Direction.CW);
		bar.reset();
		bar.addRoundRect(cx - al, cy - aw, cx + al, cy + aw, aw * 0.85f, aw * 0.85f, Path.Direction.CW);
		path.op(bar, Path.Op.UNION);

		p.setStyle(Paint.Style.FILL);
		p.setShader(null);
		p.setColor(Color.argb(140, 0, 0, 0));
		c.save();
		c.translate(0f, r * 0.05f);
		c.drawPath(path, p);
		c.restore();
		shade(new LinearGradient(0f, cy - al, 0f, cy + al, 0xFF575757, 0xFF1C1C1C, Shader.TileMode.CLAMP));
		c.drawPath(path, p);

		float tx = 0f, ty = 0f;
		for (int i = 0; i < 4; i++) {
			if ((bits & DIR_BITS[i]) != 0) {
				tx += AXES[i][0];
				ty += AXES[i][1];
			}
		}
		float tl = (float) Math.hypot(tx, ty);
		if (tl > 0f && t > 0.001f) {
			float ux = tx / tl, uy = ty / tl;
			c.save();
			c.clipPath(path);
			shade(new LinearGradient(
					cx + ux * al, cy + uy * al, cx - ux * al, cy - uy * al,
					new int[]{Color.argb((int) (200 * t), 0, 0, 0), Color.argb(0, 0, 0, 0),
							Color.argb(0, 255, 255, 255), Color.argb((int) (70 * t), 255, 255, 255)},
					new float[]{0f, 0.5f, 0.5f, 1f}, Shader.TileMode.CLAMP));
			c.drawRect(cx - al, cy - al, cx + al, cy + al, p);
			c.restore();
		}
		p.setShader(null);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1.5f, r * 0.02f));
		p.setColor(Color.argb(150, 0, 0, 0));
		c.drawPath(path, p);

		float dish = aw * 0.75f;
		p.setStyle(Paint.Style.FILL);
		shade(new LinearGradient(0f, cy - dish, 0f, cy + dish, 0xFF1A1A1A, 0xFF383838, Shader.TileMode.CLAMP));
		c.drawCircle(cx, cy, dish, p);
		p.setShader(null);
		c.restoreToCount(save);
	}

	/** Analogico: base escura + tampa concava com sulcos em cruz. kx, ky em -1..1. */
	void stick(Canvas c, float sx, float sy, float r, float kx, float ky, float t, float opacity) {
		int save = begin(c, sx, sy, r * 1.2f, opacity);
		socket(c, sx, sy, r, WELL_FILL);
		float kr = r * 0.58f * (1f - 0.04f * t);
		float travel = r - kr * 0.75f;
		float x = sx + kx * travel;
		float y = sy + ky * travel;

		p.setStyle(Paint.Style.FILL);
		shade(new RadialGradient(x, y, kr * 1.2f,
				new int[]{Color.argb(110, 0, 0, 0), Color.argb(110, 0, 0, 0), Color.argb(0, 0, 0, 0)},
				new float[]{0f, 0.8f, 1f}, Shader.TileMode.CLAMP));
		c.drawCircle(x, y, kr * 1.2f, p);
		shade(new LinearGradient(0f, y - kr, 0f, y + kr,
				new int[]{0xFF5E5E5E, 0xFF323232, 0xFF161616},
				new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
		c.drawCircle(x, y, kr, p);
		float dish = kr * 0.74f;
		shade(new LinearGradient(0f, y - dish, 0f, y + dish, 0xFF1C1C1C, 0xFF3A3A3A, Shader.TileMode.CLAMP));
		c.drawCircle(x, y, dish, p);
		p.setShader(null);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeWidth(kr * 0.07f);
		p.setColor(0xFF121212);
		for (float[] a : AXES) {
			c.drawLine(x + a[0] * kr * 0.2f, y + a[1] * kr * 0.2f, x + a[0] * kr * 0.6f, y + a[1] * kr * 0.6f, p);
		}
		p.setStrokeCap(Paint.Cap.BUTT);
		c.restoreToCount(save);
	}

	// ------------------------------------------------------------ pecas

	private static Jewel jewelFor(String label) {
		if (label == null) return DARK;
		switch (label) {
			case "A":
				return GREEN;
			case "B":
				return RED;
			case "C":
			case "X":
				return BLUE;
			case "D":
			case "Y":
				return YELLOW;
			default:
				return DARK;
		}
	}

	private int begin(Canvas c, float cx, float cy, float ext, float opacity) {
		if (opacity >= 0.995f) return c.save();
		int a = Math.max(0, Math.min(255, Math.round(255 * opacity)));
		return c.saveLayerAlpha(cx - ext, cy - ext, cx + ext, cy + ext, a);
	}

	private void fit(String s, float size, float maxW) {
		text.setTextSize(size);
		float w = text.measureText(s);
		if (w > maxW) text.setTextSize(size * maxW / w);
	}

	private void socket(Canvas c, float cx, float cy, float r, int fill) {
		p.setShader(null);
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.argb(fill, 10, 10, 10));
		c.drawCircle(cx, cy, r, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(2f, r * 0.03f));
		p.setColor(Color.argb(80, 255, 255, 255));
		c.drawCircle(cx, cy, r, p);
	}

	private void jewel(Canvas c, float cx, float cy, float r0, Jewel j, float t) {
		float r = r0 * (1f - 0.06f * t);
		p.setStyle(Paint.Style.FILL);
		shade(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.45f,
				new int[]{mix(j.light, j.base, t), j.base, j.dark},
				new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
		c.drawCircle(cx, cy, r, p);
		p.setShader(null);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(r * 0.06f);
		p.setColor(withAlpha(j.dark, 200));
		c.drawCircle(cx, cy, r * 0.97f, p);
		rect.set(cx - r * 0.55f, cy - r * 0.84f, cx + r * 0.55f, cy - r * 0.16f);
		p.setStyle(Paint.Style.FILL);
		shade(new LinearGradient(0f, rect.top, 0f, rect.bottom,
				Color.argb((int) (150 - 90 * t), 255, 255, 255), Color.argb(0, 255, 255, 255),
				Shader.TileMode.CLAMP));
		c.drawOval(rect, p);
		p.setShader(null);
	}

	private void pill(Canvas c, RectF r, String label, float t) {
		float cx = r.centerX(), cy = r.centerY();
		float pad = r.height() * 0.09f;
		float hw = r.width() / 2f - pad;
		float hh = r.height() / 2f - pad;
		p.setShader(null);
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.argb(150, 10, 10, 10));
		c.drawRoundRect(r, hh + pad, hh + pad, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(2f, hh * 0.06f));
		p.setColor(Color.argb(80, 255, 255, 255));
		c.drawRoundRect(r, hh + pad, hh + pad, p);

		rect.set(cx - hw, cy - hh, cx + hw, cy + hh);
		p.setStyle(Paint.Style.FILL);
		shade(new LinearGradient(0f, rect.top, 0f, rect.bottom,
				mix(0xFF505050, 0xFF2A2A2A, t), 0xFF0E0E0E, Shader.TileMode.CLAMP));
		c.drawRoundRect(rect, hh, hh, p);
		rect.set(cx - hw * 0.7f, cy - hh * 0.85f, cx + hw * 0.7f, cy - hh * 0.1f);
		shade(new LinearGradient(0f, rect.top, 0f, rect.bottom,
				Color.argb((int) (90 - 60 * t), 255, 255, 255), Color.argb(0, 255, 255, 255),
				Shader.TileMode.CLAMP));
		c.drawOval(rect, p);
		p.setShader(null);

		if (label != null && !label.isEmpty()) {
			fit(label, hh * 0.8f, hw * 1.6f);
			text.setColor(mix(Color.argb(230, 225, 225, 225), Color.argb(200, 170, 170, 170), t));
			c.drawText(label, cx, cy + text.getTextSize() * 0.36f, text);
		}
	}

	/** Preenchimento com gradiente: a cor do paint multiplica o shader, entao fica opaca. */
	private void shade(Shader s) {
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.BLACK);
		p.setShader(s);
	}

	private static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	private static int mix(int a, int b, float t) {
		return Color.argb(
				(int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
				(int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
				(int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
				(int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
	}
}
