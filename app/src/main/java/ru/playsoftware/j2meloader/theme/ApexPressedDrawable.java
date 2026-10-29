package ru.playsoftware.j2meloader.theme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import javax.microedition.util.ContextHolder;

/** Destaque ao pressionar/focar um item, com a cor escolhida em "cor ao pressionar". */
public class ApexPressedDrawable extends Drawable {
	private final Paint paint = new Paint();
	private boolean active;

	public ApexPressedDrawable() {
	}

	@Override
	public boolean isStateful() {
		return true;
	}

	@Override
	protected boolean onStateChange(int[] state) {
		boolean a = false;
		for (int s : state) {
			if (s == android.R.attr.state_pressed || s == android.R.attr.state_focused
					|| s == android.R.attr.state_activated || s == android.R.attr.state_selected) {
				a = true;
				break;
			}
		}
		if (a != active) {
			active = a;
			invalidateSelf();
			return true;
		}
		return false;
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		if (active) {
			paint.setColor(ApexTheme.pressedColor(ContextHolder.getAppContext()));
			canvas.drawRect(getBounds(), paint);
		}
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
