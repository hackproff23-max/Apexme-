package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.widget.LinearLayout;

import javax.microedition.lcdui.Canvas;
import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;

/** Controles extras do shader: gama e suavidade das curvas (aparecem em Controle de cores). */
final class ApexShaderExtras {
	private ApexShaderExtras() {
	}

	static void add(Activity a, LinearLayout root) {
		float[] ex = {ApexPrefs.gamma(), ApexPrefs.curveSoftness()};
		root.addView(ApexUi.slider(a, R.drawable.apexlogo_colors, "Gama", 50, 200,
				Math.round(ex[0] * 100), "%", v -> {
					ex[0] = v / 100f;
					ApexPrefs.setExtraValues(ex[0], ex[1]);
					apply(a);
				}));
		root.addView(ApexUi.slider(a, R.drawable.apexlogo_shaders, "Suavidade das curvas", 5, 100,
				Math.round(ex[1] * 100), "%", v -> {
					ex[1] = v / 100f;
					ApexPrefs.setExtraValues(ex[0], ex[1]);
					apply(a);
				}));
		root.addView(ApexUi.text(a, "Gama 100% = normal. Suavidade das curvas (padrão 45%) só muda o " +
				"shader 4k resolution. Segure qualquer controle deslizante para a janela ficar " +
				"transparente e você ver o jogo.", 12));
	}

	private static void apply(Activity a) {
		if (a instanceof MicroActivity) {
			MicroActivity m = (MicroActivity) a;
			if (m.getCurrent() instanceof Canvas) {
				((Canvas) m.getCurrent()).applyApexShader(false);
			}
		}
	}
}
