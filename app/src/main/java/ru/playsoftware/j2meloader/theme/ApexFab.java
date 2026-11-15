package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

import androidx.core.widget.ImageViewCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Map;
import java.util.WeakHashMap;

/** Botoes flutuantes (importar jogo / pasta): logo colorido, ou icone com contraste sobre o fundo do tema. */
final class ApexFab {
	private static final Map<View, Integer> DONE = new WeakHashMap<>();

	private ApexFab() {
	}

	static void style(FloatingActionButton fab, int tint) {
		Context c = fab.getContext();
		int icon;
		int fill;
		if (tint != 0) {
			icon = tint;
			fill = tint == 0xFF000000 ? 0xFFFFFFFF : 0xFF000000;
		} else {
			icon = ApexTheme.textColor(c);
			fill = ApexTheme.windowColor(c);
		}
		// botoes marcados como "apexlogo" usam o logo colorido: so o aro (fundo) segue o tema
		boolean logo = "apexlogo".equals(fab.getTag());
		int key = icon * 31 + fill + (logo ? 1 : 0);
		Integer old = DONE.get(fab);
		if (old != null && old == key) {
			return;
		}
		DONE.put(fab, key);
		ImageViewCompat.setImageTintList(fab, logo ? null : ColorStateList.valueOf(icon));
		fab.setBackgroundTintList(ColorStateList.valueOf(fill));
	}
}
