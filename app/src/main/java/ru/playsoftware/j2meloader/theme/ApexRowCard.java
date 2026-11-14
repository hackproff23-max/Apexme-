package ru.playsoftware.j2meloader.theme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/** Cartao do tema (ApexCardBackground) com margem interna, sem alterar o padding da view. */
public class ApexRowCard extends Drawable {
	private final ApexCardBackground inner = new ApexCardBackground();
	private final int ih;
	private final int iv;

	public ApexRowCard(int insetH, int insetV) {
		ih = insetH;
		iv = insetV;
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Rect b = getBounds();
		inner.setBounds(b.left + ih, b.top + iv, b.right - ih, b.bottom - iv);
		inner.draw(canvas);
	}

	@Override
	public boolean isStateful() {
		return true;
	}

	@Override
	protected boolean onStateChange(int[] state) {
		inner.setState(state);
		invalidateSelf();
		return true;
	}

	@Override
	public void setAlpha(int alpha) {
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
