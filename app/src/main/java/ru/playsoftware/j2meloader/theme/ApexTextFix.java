package ru.playsoftware.j2meloader.theme;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Aplica fonte, tamanho e contorno do tema numa arvore de views e diz se algo mudou o layout. */
final class ApexTextFix {
	private ApexTextFix() {
	}

	/** Devolve true se trocou fonte ou tamanho de algum texto (precisa de novo layout). */
	static boolean fixTree(View v) {
		boolean changed = false;
		if (v instanceof TextView) {
			TextView tv = (TextView) v;
			Typeface t = ApexTheme.boldTypeface(tv.getContext());
			if (tv.getTypeface() != t) {
				ApexTheme.applyFont(tv);
				changed = true;
			}
			float before = tv.getTextSize();
			ApexTheme.applyTextSize(tv);
			if (Math.abs(before - tv.getTextSize()) > 0.01f) {
				changed = true;
			}
			ApexTheme.applyOutline(tv);
		}
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				if (fixTree(g.getChildAt(i))) {
					changed = true;
				}
			}
		}
		return changed;
	}
}
