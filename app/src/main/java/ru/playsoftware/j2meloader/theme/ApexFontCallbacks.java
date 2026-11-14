package ru.playsoftware.j2meloader.theme;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Garante a fonte do app (negrito, sem italico), o tamanho e o contorno em todos os textos.
 * A correcao roda ANTES de cada desenho (OnPreDrawListener), entao o texto nunca aparece primeiro
 * "cru" e depois muda. Janelas novas (menus, dialogos) recebem o gancho antes do primeiro quadro.
 */
public final class ApexFontCallbacks implements Application.ActivityLifecycleCallbacks {
	private final Map<View, Boolean> hooked = new WeakHashMap<>();
	private int resumed;
	private boolean running;
	private boolean scanFailed;
	private Object wmgInstance;
	private java.lang.reflect.Field viewsField;

	private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
		@Override
		public void doFrame(long frameTimeNanos) {
			if (resumed <= 0) {
				running = false;
				return;
			}
			scanWindows();
			Choreographer.getInstance().postFrameCallback(this);
		}
	};

	/** Liga o gancho em todas as janelas do app (decor de cada atividade, dialogos e popups). */
	@SuppressWarnings("unchecked")
	private void scanWindows() {
		if (scanFailed) {
			return;
		}
		try {
			if (viewsField == null) {
				Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
				wmgInstance = wmg.getMethod("getInstance").invoke(null);
				viewsField = wmg.getDeclaredField("mViews");
				viewsField.setAccessible(true);
			}
			List<View> views = (List<View>) viewsField.get(wmgInstance);
			for (int i = 0; i < views.size(); i++) {
				hook(views.get(i));
			}
		} catch (Throwable t) {
			scanFailed = true;
		}
	}

	private void hook(final View root) {
		if (root == null || hooked.containsKey(root)) {
			return;
		}
		ViewTreeObserver o = root.getViewTreeObserver();
		if (!o.isAlive()) {
			return;
		}
		hooked.put(root, Boolean.TRUE);
		final boolean[] dirty = {true};
		final int[] cancels = {0};
		o.addOnGlobalLayoutListener(() -> dirty[0] = true);
		o.addOnPreDrawListener(() -> {
			if (!dirty[0]) {
				return true;
			}
			dirty[0] = false;
			boolean changed = ApexTextFix.fixTree(root);
			android.view.ViewGroup.LayoutParams lp = root.getLayoutParams();
			if (lp instanceof WindowManager.LayoutParams) {
				int type = ((WindowManager.LayoutParams) lp).type;
				if (type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW
						&& type <= WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG) {
					ApexTheme.applyPopupTree(root);
				}
			}
			// se mudou fonte/tamanho, cancela este quadro: o layout refeito e desenhado ja certo
			if (changed && cancels[0] < 3) {
				cancels[0]++;
				return false;
			}
			cancels[0] = 0;
			return true;
		});
	}

	@Override
	public void onActivityCreated(Activity a, Bundle state) {
		hook(a.getWindow().getDecorView());
	}

	@Override
	public void onActivityStarted(Activity a) {
	}

	@Override
	public void onActivityResumed(Activity a) {
		resumed++;
		hook(a.getWindow().getDecorView());
		if (!running) {
			running = true;
			Choreographer.getInstance().postFrameCallback(frame);
		}
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
