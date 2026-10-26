package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import javax.microedition.util.ContextHolder;

/** Fundo das telas do app: cor escolhida ou imagem importada. Usado via {@code <drawable class=...>}. */
public class ApexAppBackground extends Drawable {
	private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

	public ApexAppBackground() {
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Context c = ContextHolder.getAppContext();
		paint.setColor(ApexTheme.bgColor(c));
		canvas.drawRect(getBounds(), paint);
		Bitmap bmp = ApexTheme.appBitmap(c);
		if (bmp != null) {
			paint.setColor(0xFFFFFFFF);
			ApexTheme.drawCenterCrop(canvas, bmp, getBounds(), paint);
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
		return PixelFormat.OPAQUE;
	}
}
