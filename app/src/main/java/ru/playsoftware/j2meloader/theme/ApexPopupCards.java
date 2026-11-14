package ru.playsoftware.j2meloader.theme;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

/** Menus de overflow e listas em janelas soltas: cada item vira um cartao, como nas configuracoes. */
final class ApexPopupCards {
	private ApexPopupCards() {
	}

	static void style(View root) {
		ListView lv = find(root);
		if (lv == null) {
			return;
		}
		float d = lv.getResources().getDisplayMetrics().density;
		if (lv.getDividerHeight() != 0) {
			lv.setDivider(null);
			lv.setDividerHeight(0);
		}
		int p = Math.round(6 * d);
		if (lv.getPaddingTop() != p) {
			lv.setPadding(p, p, p, p);
			lv.setClipToPadding(false);
			lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
		}
		int ih = Math.round(2 * d);
		int iv = Math.round(3 * d);
		for (int i = 0; i < lv.getChildCount(); i++) {
			View row = lv.getChildAt(i);
			if (!(row.getBackground() instanceof ApexRowCard)) {
				row.setBackground(new ApexRowCard(ih, iv));
			}
		}
	}

	private static ListView find(View v) {
		if (v instanceof ListView) {
			return (ListView) v;
		}
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				ListView r = find(g.getChildAt(i));
				if (r != null) {
					return r;
				}
			}
		}
		return null;
	}
}
