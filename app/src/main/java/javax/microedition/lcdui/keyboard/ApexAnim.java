package javax.microedition.lcdui.keyboard;

import android.view.View;

/** Valores animados (0..1 ou livres) que pedem redesenho enquanto nao chegam ao alvo. */
final class ApexAnim {
	private static View view;

	private ApexAnim() {
	}

	static void bind(View v) {
		if (v != null) view = v;
	}

	private static void request() {
		View v = view;
		if (v != null) v.postInvalidateOnAnimation();
	}

	static final class Value {
		float v;
		private long last;

		private float dt() {
			long n = System.nanoTime();
			float d = last == 0 ? 0f : (n - last) / 1e9f;
			last = n;
			if (d > 0.1f) d = 0.016f; // ficou parado: nao pula a animacao
			return d;
		}

		/** Sobe em ~80 ms ao pressionar, desce em ~200 ms ao soltar. */
		float linear(boolean on) {
			float step = dt() / (on ? 0.08f : 0.20f);
			v = on ? Math.min(1f, v + step) : Math.max(0f, v - step);
			if (v != (on ? 1f : 0f)) request();
			return v;
		}

		/** Aproximacao suave ao alvo; snap=true cola no alvo (dedo controlando). */
		float smooth(float target, boolean snap) {
			float d = dt();
			if (snap) {
				v = target;
			} else {
				v += (target - v) * (1f - (float) Math.exp(-d * 16f));
				if (Math.abs(target - v) < 0.002f) v = target;
			}
			if (v != target) request();
			return v;
		}
	}
}
