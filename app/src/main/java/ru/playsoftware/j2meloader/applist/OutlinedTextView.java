package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

import ru.playsoftware.j2meloader.theme.ApexTheme;

/**
 * TextView com contorno: desenha o texto em modo STROKE e depois o preenchimento normal por cima.
 * As cores e a fonte vem de ApexTheme (janela "Cores").
 */
public class OutlinedTextView extends AppCompatTextView {
	/** Desfoque maximo da sombra (dp): valores grandes deixam o desenho do texto lento e piscando. */
	private static final int MAX_SHADOW_BLUR_DP = 6;

	private static int shadowVersion = -1;
	private static boolean shadowOn;
	private static float shadowRadius;
	private static float shadowDx;
	private static float shadowDy;
	private static int shadowColor;

	private final int outlineColor;

	public OutlinedTextView(Context context) {
		this(context, null);
	}

	public OutlinedTextView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.textViewStyle);
	}

	public OutlinedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		outlineColor = ApexTheme.outlineColor(context);
		setTextColor(ApexTheme.textColor(context));
		ApexTheme.applyFont(this);
		// folga em volta do texto para o contorno nunca ser cortado nas bordas
		float d = context.getResources().getDisplayMetrics().density;
		int h = Math.round(3 * d);
		int v = Math.round(2 * d);
		setPaddingRelative(getPaddingStart() + h, getPaddingTop() + v, getPaddingEnd() + h, getPaddingBottom() + v);
	}

	@Override
	public void setTextAppearance(Context context, int resId) {
		super.setTextAppearance(context, resId);
		ApexTheme.applyFont(this);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		drawOutline(this, canvas, outlineColor, false);
		super.onDraw(canvas);
	}

	/** Calcula (com cache por versao do tema) a sombra, com o desfoque limitado. */
	private static synchronized boolean refreshShadow(Context c) {
		int version = ApexTheme.version();
		if (shadowVersion != version) {
			float d = c.getResources().getDisplayMetrics().density;
			int color = ApexTheme.getColor(c, ApexTheme.K_SHADOW_COLOR, ApexTheme.DEF_SHADOW_COLOR);
			int opacity = Math.max(0, Math.min(100,
					ApexTheme.getColor(c, ApexTheme.K_SHADOW_ALPHA, ApexTheme.DEF_SHADOW_ALPHA)));
			int alpha = Math.round(Color.alpha(color) * opacity / 100f);
			shadowColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
			shadowOn = alpha > 0;
			int blur = Math.max(0, ApexTheme.getColor(c, ApexTheme.K_SHADOW_BLUR, ApexTheme.DEF_SHADOW_BLUR));
			shadowRadius = Math.max(0.01f, Math.min(blur, MAX_SHADOW_BLUR_DP) * d);
			shadowDx = ApexTheme.getColor(c, ApexTheme.K_SHADOW_DX, ApexTheme.DEF_SHADOW_DX) * d;
			shadowDy = ApexTheme.getColor(c, ApexTheme.K_SHADOW_DY, ApexTheme.DEF_SHADOW_DY) * d;
			shadowVersion = version;
		}
		return shadowOn;
	}

	/**
	 * Desenha o contorno do texto de qualquer TextView. Com fill=true desenha tambem o preenchimento
	 * por cima (usado na sobreposicao dos textos que nao sao OutlinedTextView).
	 */
	public static void drawOutline(TextView tv, Canvas canvas, int outlineColor, boolean fill) {
		Layout layout = tv.getLayout();
		if (layout == null) {
			return;
		}
		Context c = tv.getContext();
		float scale = ApexTheme.outlineScale(c);
		boolean outline = scale > 0.001f;
		boolean shadow = refreshShadow(c);
		if (!outline && !shadow) {
			return;
		}
		TextPaint paint = tv.getPaint();
		int save = canvas.save();
		canvas.translate(tv.getCompoundPaddingLeft(), tv.getExtendedPaddingTop() + verticalOffset(tv, layout));
		paint.setStrokeJoin(Paint.Join.ROUND);
		if (shadow) {
			// a sombra sai so desta primeira passada (por baixo do contorno e do preenchimento)
			synchronized (OutlinedTextView.class) {
				paint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor);
			}
		}
		if (outline) {
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(Math.max(tv.getResources().getDisplayMetrics().density, tv.getTextSize() * 0.14f) * scale);
			paint.setColor(outlineColor);
		} else {
			paint.setStyle(Paint.Style.FILL);
			paint.setStrokeWidth(0f);
			paint.setColor(tv.getCurrentTextColor());
		}
		layout.draw(canvas);
		if (shadow) {
			restoreShadow(tv, paint);
		}
		paint.setStyle(Paint.Style.FILL);
		paint.setStrokeWidth(0f);
		if (fill && outline) {
			paint.setColor(tv.getCurrentTextColor());
			layout.draw(canvas);
		}
		canvas.restoreToCount(save);
	}

	/** Volta a sombra propria do TextView (se tiver uma); senao remove. */
	private static void restoreShadow(TextView tv, TextPaint paint) {
		if (Build.VERSION.SDK_INT >= 26 && tv.getShadowRadius() > 0f) {
			paint.setShadowLayer(tv.getShadowRadius(), tv.getShadowDx(), tv.getShadowDy(), tv.getShadowColor());
		} else {
			paint.clearShadowLayer();
		}
	}

	/** Mesmo deslocamento vertical que o TextView usa ao centralizar/alinhar embaixo. */
	private static int verticalOffset(TextView tv, Layout layout) {
		int box = tv.getMeasuredHeight() - tv.getExtendedPaddingTop() - tv.getExtendedPaddingBottom();
		int text = layout.getHeight();
		if (text >= box) {
			return 0;
		}
		int g = tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
		if (g == Gravity.CENTER_VERTICAL) {
			return (box - text) / 2;
		} else if (g == Gravity.BOTTOM) {
			return box - text;
		}
		return 0;
	}
}
