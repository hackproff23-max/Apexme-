package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import javax.microedition.util.ContextHolder;

/**
 * Cartao arredondado e translucido (estilo das janelas de configuracao).
 * Tema claro: branco; tema escuro: preto; tema personalizado: cor da janela.
 * Nao desenha imagem nenhuma: a imagem de fundo da janela continua aparecendo por tras.
 */
public class ApexCardBackground extends Drawable {
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	private boolean pressed;

	public ApexCardBackground() {
	}

	private static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Context c = ContextHolder.getAppContext();
		float d = c.getResources().getDisplayMetrics().density;
		float radius = d * 14f;
		float stroke = d;
		int fill;
		int edge;
		int mode = ApexThemeMode.current(c);
		if (mode == ApexThemeMode.MODE_LIGHT) {
			fill = 0xB8FFFFFF;
			edge = 0xCCFFFFFF;
		} else if (mode == ApexThemeMode.MODE_DARK) {
			fill = 0x99000000;
			edge = 0x33FFFFFF;
		} else {
			fill = withAlpha(ApexTheme.windowColor(c), 0x99);
			edge = withAlpha(ApexTheme.textColor(c), 0x40);
		}
		rect.set(getBounds());
		rect.inset(stroke / 2f, stroke / 2f);

		paint.setStyle(Paint.Style.FILL);
		paint.setColor(fill);
		canvas.drawRoundRect(rect, radius, radius, paint);
		if (pressed) {
			paint.setColor(withAlpha(ApexTheme.pressedColor(c), 0x70));
			canvas.drawRoundRect(rect, radius, radius, paint);
		}
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(stroke);
		paint.setColor(edge);
		canvas.drawRoundRect(rect, radius, radius, paint);
	}

	@Override
	public boolean isStateful() {
		return true;
	}

	@Override
	protected boolean onStateChange(int[] state) {
		boolean p = false;
		for (int s : state) {
			if (s == android.R.attr.state_pressed) {
				p = true;
				break;
			}
		}
		if (p != pressed) {
			pressed = p;
			invalidateSelf();
			return true;
		}
		return false;
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
