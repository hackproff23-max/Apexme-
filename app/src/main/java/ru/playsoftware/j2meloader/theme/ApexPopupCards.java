package ru.playsoftware.j2meloader.theme;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Menus de overflow e listas em janelas soltas: cada item vira um cartao, como nas configuracoes,
 * e leva o logo colorido do item (no release o Android nao mostra os icones do menu sozinho).
 */
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
			addLogo(lv, lv.getFirstVisiblePosition() + i, row, d);
		}
	}

	/** Poe o logo colorido do item (apexlogo_*) antes do nome, se o menu ainda nao mostrou o icone. */
	private static void addLogo(ListView lv, int position, View row, float d) {
		Object item = lv.getItemAtPosition(position);
		if (!(item instanceof MenuItem)) {
			return;
		}
		Drawable icon = ((MenuItem) item).getIcon();
		if (icon == null) {
			return;
		}
		TextView title = firstText(row);
		if (title == null || title.getCompoundDrawablesRelative()[0] != null || hasMenuIcon(row)) {
			return;
		}
		int s = Math.round(30 * d);
		icon.setBounds(0, 0, s, s);
		title.setCompoundDrawablesRelative(icon, null, null, null);
		title.setCompoundDrawablePadding(Math.round(12 * d));
	}

	/** True se o proprio menu ja desenhou um icone (ImageView visivel) nessa linha. */
	private static boolean hasMenuIcon(View row) {
		if (!(row instanceof ViewGroup)) {
			return false;
		}
		ViewGroup g = (ViewGroup) row;
		for (int i = 0; i < g.getChildCount(); i++) {
			View c = g.getChildAt(i);
			if (c instanceof ImageView && c.getVisibility() == View.VISIBLE
					&& ((ImageView) c).getDrawable() != null) {
				return true;
			}
		}
		return false;
	}

	private static TextView firstText(View v) {
		if (v instanceof TextView && ((TextView) v).getText().length() > 0) {
			return (TextView) v;
		}
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				TextView t = firstText(g.getChildAt(i));
				if (t != null) {
					return t;
				}
			}
		}
		return null;
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
