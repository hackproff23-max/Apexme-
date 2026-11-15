package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.util.Random;

import ru.playsoftware.j2meloader.theme.ApexTheme;

/**
 * Barra de progresso do Apexme, com cara de emulador:
 * trilho em capsula, vida verde com listras que andam, brilho, 10 divisorias (como barra de HP),
 * faiscas de pixel saindo da ponta, clarao ao chegar em 100% e o numero seguindo a ponta verde.
 *
 * O progresso real e informado em passos (setProgress). Entre um passo e outro a barra avanca
 * devagar sozinha (ate creepLimit), para nunca parecer travada.
 */
public class ApexProgressBar extends View {
	private static final int MAX_SPARKS = 28;

	private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

	private final RectF inner = new RectF();
	private final RectF fillRect = new RectF();
	private final RectF borderRect = new RectF();
	private final Path clipPath = new Path();
	private final Path stripe = new Path();
	private final Random rnd = new Random();

	// 0..1
	private float target;
	private float limit;
	private float shown;
	private float phase;
	private float flash;
	private float spawn;
	private boolean wasFull;
	private boolean fast;

	// x, y, vx, vy, vida
	private final float[] sparks = new float[MAX_SPARKS * 5];

	private final float d;
	private final boolean darkTheme;
	private long lastNs;
	private boolean running;
	private final Runnable stepper = this::step;

	public ApexProgressBar(Context c) {
		this(c, null);
	}

	public ApexProgressBar(Context c, AttributeSet attrs) {
		super(c, attrs);
		d = c.getResources().getDisplayMetrics().density;
		// tema escuro = texto claro
		darkTheme = ColorUtils.calculateLuminance(ApexTheme.textColor(c)) > 0.5;

		TextView tv = new TextView(c);
		ApexTheme.applyFont(tv);
		Typeface tf = tv.getTypeface();
		textFill.setColor(Color.WHITE);
		textFill.setTextSize(16 * c.getResources().getDisplayMetrics().scaledDensity);
		textFill.setTextAlign(Paint.Align.CENTER);
		textStroke.set(textFill);
		textStroke.setStyle(Paint.Style.STROKE);
		textStroke.setStrokeJoin(Paint.Join.ROUND);
		textStroke.setStrokeWidth(3.4f * d);
		textStroke.setColor(Color.BLACK);
		if (tf != null) {
			textFill.setTypeface(tf);
			textStroke.setTypeface(tf);
		}

		trackPaint.setColor(darkTheme ? 0xFF101317 : Color.WHITE);
		stripePaint.setColor(0x3CFFFFFF);
		tickPaint.setColor(darkTheme ? 0x55FFFFFF : 0x30000000);
		tickPaint.setStrokeWidth(Math.max(1f, d));
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setStrokeWidth(3.5f * d);
		flashPaint.setColor(Color.WHITE);
		pointerPaint.setStyle(Paint.Style.FILL);
		sparkPaint.setStyle(Paint.Style.FILL);
	}

	/**
	 * @param base       progresso garantido (0..1), a barra nunca fica abaixo disso
	 * @param creepLimit ate onde ela pode avancar sozinha enquanto espera o proximo passo
	 */
	public void setProgress(float base, float creepLimit) {
		base = Math.max(0f, Math.min(1f, base));
		if (base > target) {
			target = base;
		}
		limit = Math.max(target, Math.min(1f, creepLimit));
	}

	/** Termina: vai a 100% mais rapido, com clarao. */
	public void complete() {
		target = 1f;
		limit = 1f;
		fast = true;
	}

	public boolean isFull() {
		return shown >= 0.999f;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int w = MeasureSpec.getSize(widthMeasureSpec);
		setMeasuredDimension(w, (int) (74 * d));
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		running = true;
		lastNs = 0;
		postOnAnimation(stepper);
	}

	@Override
	protected void onDetachedFromWindow() {
		running = false;
		removeCallbacks(stepper);
		super.onDetachedFromWindow();
	}

	private void step() {
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1e9f);
		lastNs = now;

		if (target < limit) {
			target += (limit - target) * (0.12f * dt);
		}
		float rate = fast ? 9f : 5f;
		shown += (target - shown) * (1f - (float) Math.exp(-dt * rate));
		if (Math.abs(target - shown) < 0.0007f) {
			shown = target;
		}
		phase = (phase + dt * 38f * d) % (22f * d);

		boolean full = shown >= 0.999f;
		if (full && !wasFull) {
			flash = 1f;
		}
		wasFull = full;
		flash = Math.max(0f, flash - dt * 1.7f);

		// faiscas (pixels) saindo da ponta verde
		if (!full && shown > 0.002f) {
			spawn += dt * 20f;
			while (spawn >= 1f) {
				spawn -= 1f;
				spawnSpark();
			}
		}
		for (int i = 0; i < MAX_SPARKS; i++) {
			int o = i * 5;
			if (sparks[o + 4] > 0f) {
				sparks[o] += sparks[o + 2] * dt;
				sparks[o + 1] += sparks[o + 3] * dt;
				sparks[o + 4] -= dt * 1.9f;
			}
		}
		invalidate();
		if (running) {
			postOnAnimation(stepper);
		}
	}

	private void spawnSpark() {
		for (int i = 0; i < MAX_SPARKS; i++) {
			int o = i * 5;
			if (sparks[o + 4] <= 0f) {
				float fx = inner.left + inner.width() * shown;
				sparks[o] = fx - rnd.nextFloat() * 5f * d;
				sparks[o + 1] = inner.top + rnd.nextFloat() * inner.height();
				sparks[o + 2] = (rnd.nextFloat() - 0.5f) * 22f * d;
				sparks[o + 3] = -(14f + rnd.nextFloat() * 40f) * d;
				sparks[o + 4] = 1f;
				return;
			}
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		float bw = 3.5f * d;
		float left = 2f * d;
		float top = 14f * d;
		float bar = 28f * d;
		inner.set(left + bw, top + bw, w - left - bw, top + bar - bw);
		borderRect.set(left + bw / 2f, top + bw / 2f, w - left - bw / 2f, top + bar - bw / 2f);
		clipPath.reset();
		float r = inner.height() / 2f;
		clipPath.addRoundRect(inner, r, r, Path.Direction.CW);

		fillPaint.setShader(new LinearGradient(0, inner.top, 0, inner.bottom,
				new int[]{0xFF8CFF55, 0xFF22F200, 0xFF12A400},
				new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
		glossPaint.setShader(new LinearGradient(0, inner.top, 0, inner.centerY(),
				0x70FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
		borderPaint.setShader(new LinearGradient(0, borderRect.top, 0, borderRect.bottom,
				darkTheme ? 0xFF5A6068 : 0xFF2B2B2B, darkTheme ? 0xFF8C939C : 0xFF4D4D4D,
				Shader.TileMode.CLAMP));
		pointerPaint.setColor(darkTheme ? 0xFF8C939C : 0xFF3A3A3A);
	}

	@Override
	protected void onDraw(Canvas c) {
		float r = inner.height() / 2f;
		float fx = inner.left + inner.width() * shown;

		// trilho
		c.drawRoundRect(inner, r, r, trackPaint);

		c.save();
		c.clipPath(clipPath);

		if (fx > inner.left + 1f) {
			fillRect.set(inner.left, inner.top, fx, inner.bottom);
			c.drawRoundRect(fillRect, r, r, fillPaint);

			// listras diagonais andando
			c.save();
			c.clipRect(fillRect);
			float period = 22f * d;
			float w = 10f * d;
			float slant = 9f * d;
			for (float x = fillRect.left - period * 2f + phase; x < fillRect.right + period; x += period) {
				stripe.reset();
				stripe.moveTo(x, inner.bottom);
				stripe.lineTo(x + w, inner.bottom);
				stripe.lineTo(x + w + slant, inner.top);
				stripe.lineTo(x + slant, inner.top);
				stripe.close();
				c.drawPath(stripe, stripePaint);
			}
			c.restore();

			// brilho de vidro na metade de cima
			c.drawRoundRect(fillRect.left, fillRect.top, fillRect.right, fillRect.centerY(),
					r, r, glossPaint);

			// brilho na ponta
			if (shown < 0.999f) {
				float gr = 24f * d;
				glowPaint.setShader(new RadialGradient(fx, inner.centerY(), gr,
						0xA0C8FF9A, 0x00C8FF9A, Shader.TileMode.CLAMP));
				c.drawCircle(fx, inner.centerY(), gr, glowPaint);
			}
		}

		// 10 divisorias, como barra de HP de jogo
		for (int i = 1; i < 10; i++) {
			float x = inner.left + inner.width() * i / 10f;
			c.drawLine(x, inner.top, x, inner.bottom, tickPaint);
		}

		// clarao ao completar
		if (flash > 0f) {
			flashPaint.setAlpha((int) (flash * 150));
			c.drawRect(inner, flashPaint);
		}
		c.restore();

		// borda
		float br = borderRect.height() / 2f;
		c.drawRoundRect(borderRect, br, br, borderPaint);

		// faiscas
		for (int i = 0; i < MAX_SPARKS; i++) {
			int o = i * 5;
			float life = sparks[o + 4];
			if (life > 0f) {
				sparkPaint.setColor(Color.argb((int) (Math.min(1f, life) * 255), 190, 255, 140));
				float s = 2.6f * d;
				c.drawRect(sparks[o], sparks[o + 1], sparks[o] + s, sparks[o + 1] + s, sparkPaint);
			}
		}

		// ponteiro + numero acompanhando a ponta verde
		String label = Math.round(shown * 100f) + "%";
		float tw = textFill.measureText(label);
		float minX = tw / 2f + 2f * d;
		float maxX = getWidth() - tw / 2f - 2f * d;
		float lx = Math.max(minX, Math.min(maxX, fx));
		float px = Math.max(borderRect.left + br, Math.min(borderRect.right - br, fx));
		float by = borderRect.bottom + 1.5f * d;
		stripe.reset();
		stripe.moveTo(px - 5f * d, by);
		stripe.lineTo(px + 5f * d, by);
		stripe.lineTo(px, by + 6f * d);
		stripe.close();
		c.drawPath(stripe, pointerPaint);

		float ty = by + 8f * d - textFill.ascent();
		c.drawText(label, lx, ty, textStroke);
		c.drawText(label, lx, ty, textFill);
	}
}
