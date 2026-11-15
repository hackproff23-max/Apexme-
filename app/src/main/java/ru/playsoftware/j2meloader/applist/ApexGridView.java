package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;

/**
 * Grade de jogos que lembra qual cartao foi tocado por ultimo. O menu do toque longo usa isso para
 * abrir compacto, colado no jogo escolhido.
 */
public class ApexGridView extends GridView {
	private static final Rect ANCHOR = new Rect();
	private static boolean has;
	private static long downTime;

	public ApexGridView(Context context) {
		super(context);
	}

	public ApexGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ApexGridView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent e) {
		if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
			has = false;
			int pos = pointToPosition((int) e.getX(), (int) e.getY());
			if (pos != INVALID_POSITION) {
				View child = getChildAt(pos - getFirstVisiblePosition());
				if (child != null) {
					int[] loc = new int[2];
					child.getLocationOnScreen(loc);
					ANCHOR.set(loc[0], loc[1], loc[0] + child.getWidth(), loc[1] + child.getHeight());
					downTime = SystemClock.uptimeMillis();
					has = true;
				}
			}
		}
		return super.dispatchTouchEvent(e);
	}

	/** Retangulo (na tela) do cartao tocado, uma unica vez. Null se nao veio de um toque na grade. */
	public static synchronized Rect takeAnchor() {
		if (!has) {
			return null;
		}
		has = false;
		if (SystemClock.uptimeMillis() - downTime > 20000) {
			return null;
		}
		return new Rect(ANCHOR);
	}
}
