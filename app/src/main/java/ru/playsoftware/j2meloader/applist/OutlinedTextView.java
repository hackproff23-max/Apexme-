package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;

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
		Typeface custom = ApexTheme.typeface(context);
		if (custom != null) {
			Typeface current = getTypeface();
			setTypeface(custom, current != null ? (current.getStyle() & Typeface.BOLD) : Typeface.BOLD);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Layout layout = getLayout();
		if (layout != null) {
			TextPaint paint = getPaint();
			int save = canvas.save();
			canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());
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
