package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;

import ru.playsoftware.j2meloader.theme.ApexTheme;

/**
 * TextView com contorno: desenha o texto em modo STROKE e depois o preenchimento normal por cima.
 * As cores e a fonte vem de ApexTheme (janela "Cores").
 */
public class OutlinedTextView extends AppCompatTextView {
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
	}

	/** Mesmo deslocamento vertical que o TextView usa ao centralizar/alinhar embaixo. */
	private int textVerticalOffset(Layout layout) {
		int box = getMeasuredHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom();
		int text = layout.getHeight();
		if (text >= box) {
			return 0;
		}
		int g = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
		if (g == Gravity.CENTER_VERTICAL) {
			return (box - text) / 2;
		} else if (g == Gravity.BOTTOM) {
			return box - text;
		}
		return 0;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Layout layout = getLayout();
		if (layout != null) {
			TextPaint paint = getPaint();
			int save = canvas.save();
			canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop() + textVerticalOffset(layout));
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeJoin(Paint.Join.ROUND);
			paint.setStrokeWidth(Math.max(getResources().getDisplayMetrics().density, getTextSize() * 0.14f));
			paint.setColor(outlineColor);
			layout.draw(canvas);
			paint.setStyle(Paint.Style.FILL);
			paint.setStrokeWidth(0f);
			canvas.restoreToCount(save);
		}
		super.onDraw(canvas);
	}
}
