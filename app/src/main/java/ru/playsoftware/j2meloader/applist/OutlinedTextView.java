package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import ru.playsoftware.j2meloader.R;

/**
 * TextView com contorno: desenha o texto em modo STROKE e depois o preenchimento normal por cima.
 * A cor do contorno vem de R.color.name_outline.
 */
public class OutlinedTextView extends AppCompatTextView {
	private final int outlineColor;
	private final float outlineWidth;

	public OutlinedTextView(Context context) {
		this(context, null);
	}

	public OutlinedTextView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.textViewStyle);
	}

	public OutlinedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		outlineColor = ContextCompat.getColor(context, R.color.name_outline);
		outlineWidth = getResources().getDisplayMetrics().density * 2.5f;
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
			paint.setStrokeWidth(outlineWidth);
			paint.setColor(outlineColor);
			layout.draw(canvas);
			paint.setStyle(Paint.Style.FILL);
			paint.setStrokeWidth(0f);
			canvas.restoreToCount(save);
		}
		super.onDraw(canvas);
	}
}
