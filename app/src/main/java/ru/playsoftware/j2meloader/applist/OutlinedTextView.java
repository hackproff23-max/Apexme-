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
			// mesmo deslocamento vertical que o TextView usa (gravity center/bottom)
			int boxH = getHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom();
			int textH = layout.getHeight();
			int voff = 0;
			int vg = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
			if (vg != Gravity.TOP && textH < boxH) {
				voff = vg == Gravity.BOTTOM ? boxH - textH : (boxH - textH) >> 1;
			}
			canvas.translate(getCompoundPaddingLeft() + getScrollX(), getExtendedPaddingTop() + voff + getScrollY());
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
