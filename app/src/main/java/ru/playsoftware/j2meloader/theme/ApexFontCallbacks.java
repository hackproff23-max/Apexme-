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
		ApexTheme.applyFontTree(a.getWindow().getDecorView());
	}

	@Override
	public void onActivityPaused(Activity a) {
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
