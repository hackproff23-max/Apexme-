package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;
import java.util.WeakHashMap;

/** Aplica fonte, tamanho, contorno e tema claro/escuro numa arvore de views e diz se algo mudou o layout. */
final class ApexTextFix {
	/** Listas ja vigiadas (as linhas novas, criadas ao rolar, recebem o tema na hora). */
	private static final Map<View, Boolean> HOOKED = new WeakHashMap<>();

	private ApexTextFix() {
	}

	/** Devolve true se trocou fonte ou tamanho de algum texto (precisa de novo layout). */
	static boolean fixTree(View root) {
		Context c = root.getContext();
		// 0 = tema claro/escuro desligado. Na tela do proprio jogo (conteudo do midlet) nao mexe;
		// dialogos e menus, mesmo dentro do jogo, recebem o tema normalmente.
		boolean skip = ApexThemeMode.inMidlet(c) && !isDialogOrPopup(root);
		int tint = skip ? 0 : ApexThemeMode.iconTint(c);
		return fix(root, tint);
	}

	/** True se a raiz e uma janela solta (dialogo ou popup) e nao a tela principal da atividade. */
	private static boolean isDialogOrPopup(View root) {
		ViewGroup.LayoutParams lp = root.getLayoutParams();
		return lp instanceof WindowManager.LayoutParams
				&& ((WindowManager.LayoutParams) lp).type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
	}

	/**
	 * Linhas de lista criadas durante a rolagem nao disparam um novo layout da janela, entao ficavam
	 * com a cor/fonte padrao (texto branco no tema claro). Aqui cada linha nova e corrigida ao entrar.
	 */
	private static void hookRecycling(ViewGroup g, final int tint) {
		if (HOOKED.containsKey(g)) {
			return;
		}
		if (g instanceof RecyclerView) {
			HOOKED.put(g, Boolean.TRUE);
			((RecyclerView) g).addOnChildAttachStateChangeListener(
					new RecyclerView.OnChildAttachStateChangeListener() {
						@Override
						public void onChildViewAttachedToWindow(View child) {
							fix(child, tint);
						}

						@Override
						public void onChildViewDetachedFromWindow(View child) {
						}
					});
		} else if (g instanceof AbsListView) {
			HOOKED.put(g, Boolean.TRUE);
			g.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
				@Override
				public void onChildViewAdded(View parent, View child) {
					fix(child, tint);
				}

				@Override
				public void onChildViewRemoved(View parent, View child) {
				}
			});
		}
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
			hookRecycling(g, tint);
			for (int i = 0; i < g.getChildCount(); i++) {
				if (fix(g.getChildAt(i), tint)) {
					changed = true;
				}
			}
		}
		return changed;
	}
}
