package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import javax.microedition.util.ContextHolder;

/** Fundo das janelas (dialogos): cantos arredondados, cor/imagem e borda com a cor do contorno. */
public class ApexWindowBackground extends Drawable {
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private final Path path = new Path();

	public ApexWindowBackground() {
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Context c = ContextHolder.getAppContext();
		float density = c.getResources().getDisplayMetrics().density;
		float stroke = density * 2f;
		float radius = density * 14f;
		RectF rect = new RectF(getBounds());
		rect.inset(stroke / 2f, stroke / 2f);

		path.reset();
		path.addRoundRect(rect, radius, radius, Path.Direction.CW);

		int save = canvas.save();
		canvas.clipPath(path);
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(ApexTheme.windowColor(c));
		canvas.drawRect(getBounds(), paint);
		Bitmap bmp = ApexTheme.windowBitmap(c);
		if (bmp != null) {
			paint.setColor(0xFFFFFFFF);
			ApexTheme.drawCenterCrop(canvas, bmp, getBounds(), paint);
		}
		canvas.restoreToCount(save);

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(stroke);
		paint.setColor(ApexTheme.outlineColor(c));
		canvas.drawPath(path, paint);
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
