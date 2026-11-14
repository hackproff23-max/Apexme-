package ru.playsoftware.j2meloader.hex;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ru.playsoftware.j2meloader.theme.ApexCardBackground;
import ru.playsoftware.j2meloader.theme.ApexRowCard;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Visual do editor hex: cartoes arredondados do tema e teclas em cartao, sem brilho no texto. */
final class HexUi {
	private HexUi() {
	}

	private static int dp(Context c, float v) {
		return Math.round(v * c.getResources().getDisplayMetrics().density);
	}

	static void padRoot(Context c, LinearLayout root) {
		int p = dp(c, 8);
		root.setPadding(p, p, p, p);
	}

	static void addHex(Context c, LinearLayout root, View hex) {
		FrameLayout card = new FrameLayout(c);
		card.setBackground(new ApexCardBackground());
		int p = dp(c, 6);
		card.setPadding(p, p, p, p);
		card.addView(hex, new FrameLayout.LayoutParams(-1, -1));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
		lp.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
		root.addView(card, lp);
	}

	static void addStatus(Context c, LinearLayout root, TextView status) {
		status.setBackground(new ApexCardBackground());
		status.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
		status.setTextSize(13);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(dp(c, 3), dp(c, 6), dp(c, 3), dp(c, 3));
		root.addView(status, lp);
	}

	static void styleKey(Context c, Button b) {
		b.setBackground(new ApexRowCard(dp(c, 3), dp(c, 3)));
		b.setTextColor(ApexTheme.textColor(c));
		b.setTag("apex_no_outline");
		b.setStateListAnimator(null);
		b.setMinHeight(dp(c, 52));
		b.setMinimumHeight(dp(c, 52));
		b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
		b.setGravity(Gravity.CENTER);
	}
}
