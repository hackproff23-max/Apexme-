package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** ImageView quadrada (altura = largura) com cantos arredondados. */
public class SquareImageView extends ImageView {

	public SquareImageView(Context context) {
		super(context);
		init();
	}

	public SquareImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public SquareImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		final float radius = 8 * getResources().getDisplayMetrics().density;
		setOutlineProvider(new ViewOutlineProvider() {
			@Override
			public void getOutline(View view, Outline outline) {
				outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
			}
		});
		setClipToOutline(true);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, widthMeasureSpec);
		int w = getMeasuredWidth();
		setMeasuredDimension(w, w);
	}
}
