package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Aplica fonte, tamanho, contorno e tema claro/escuro numa arvore de views e diz se algo mudou o layout. */
final class ApexTextFix {
	private ApexTextFix() {
	}

	/** Devolve true se trocou fonte ou tamanho de algum texto (precisa de novo layout). */
	static boolean fixTree(View root) {
		Context c = root.getContext();
		// 0 = tema claro/escuro desligado (ou janela do jogo): nada de cores forcadas
		int tint = ApexThemeMode.inMidlet(c) ? 0 : ApexThemeMode.iconTint(c);
		return fix(root, tint);
	}

	private static boolean fix(View v, int tint) {
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
		if (tint != 0) {
			ApexThemeMode.tintView(v, tint);
		}
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				if (fix(g.getChildAt(i), tint)) {
					changed = true;
				}
			}
		}
		return changed;
	}
}
