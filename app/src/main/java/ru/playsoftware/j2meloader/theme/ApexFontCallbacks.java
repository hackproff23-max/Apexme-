package ru.playsoftware.j2meloader.theme;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Garante a fonte do app (negrito, sem italico) em todos os textos das telas, inclusive os criados
 * por codigo e os da barra superior: a cada layout confere os textos e troca so os que diferem.
 */
public final class ApexFontCallbacks implements Application.ActivityLifecycleCallbacks {
	private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
	private int resumed;
	private boolean scanFailed;

	/** Menus/popups vivem em janelas proprias: varre todas as janelas do app e aplica fonte e contorno. */
	private final Runnable scan = new Runnable() {
		@Override
		@SuppressWarnings("unchecked")
		public void run() {
			if (resumed <= 0 || scanFailed) {
				return;
			}
			try {
				Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
				Object inst = wmg.getMethod("getInstance").invoke(null);
				java.lang.reflect.Field f = wmg.getDeclaredField("mViews");
				f.setAccessible(true);
				java.util.List<View> views = new java.util.ArrayList<>((java.util.List<View>) f.get(inst));
				for (View v : views) {
					ApexTheme.applyFontTree(v);
				}
			} catch (Throwable t) {
				scanFailed = true;
				return;
			}
			handler.postDelayed(this, 300);
		}
	};

	@Override
	public void onActivityCreated(Activity a, Bundle state) {
		View decor = a.getWindow().getDecorView();
		decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				ApexTheme.applyFontTree(decor);
			}
		});
	}

	@Override
	public void onActivityStarted(Activity a) {
	}

	@Override
	public void onActivityResumed(Activity a) {
		resumed++;
		handler.removeCallbacks(scan);
		handler.post(scan);
		ApexTheme.applyFontTree(a.getWindow().getDecorView());
	}

	@Override
	public void onActivityPaused(Activity a) {
		resumed--;
	}

	@Override
	public void onActivityStopped(Activity a) {
	}

	@Override
	public void onActivitySaveInstanceState(Activity a, Bundle state) {
	}

	@Override
	public void onActivityDestroyed(Activity a) {
	}
}
