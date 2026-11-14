package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.theme.ApexWindowBackground;

/**
 * Menu em cartoes no estilo da tela de Configuracoes: titulo e uma linha por opcao
 * (logo colorido, nome e seta ou marca de selecionado).
 */
public final class ApexCardMenu {
	private ApexCardMenu() {
	}

	public static final class Item {
		final int icon;
		final String label;
		final boolean checked;
		final Runnable action;

		public Item(int icon, String label, Runnable action) {
			this(icon, label, false, action);
		}

		public Item(int icon, String label, boolean checked, Runnable action) {
			this.icon = icon;
			this.label = label;
			this.checked = checked;
			this.action = action;
		}
	}

	public static void show(Activity a, String title, Item... items) {
		if (a.isFinishing()) {
			return;
		}
		Dialog d = new Dialog(a);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);

		LinearLayout list = new LinearLayout(a);
		list.setOrientation(LinearLayout.VERTICAL);
		int pad = ApexUi.dp(a, 16);
		list.setPadding(pad, pad, pad, pad);

		TextView t = new TextView(a);
		t.setText(title);
		t.setTextSize(24);
		t.setTextColor(ApexTheme.textColor(a));
		t.setPadding(ApexUi.dp(a, 4), 0, 0, ApexUi.dp(a, 6));
		ApexTheme.applyFont(t);
		list.addView(t);

		for (Item it : items) {
			list.addView(row(a, it, () -> {
				d.dismiss();
				if (it.action != null) {
					it.action.run();
				}
			}));
		}

		ScrollView scroll = new ScrollView(a);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.addView(list);
		FrameLayout frame = new FrameLayout(a);
		frame.setBackground(new ApexWindowBackground());
		frame.addView(scroll, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
		d.setContentView(frame);

		Window w = d.getWindow();
		if (w != null) {
			w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			w.setGravity(Gravity.CENTER);
			WindowManager.LayoutParams lp = w.getAttributes();
			int screenW = a.getResources().getDisplayMetrics().widthPixels;
			lp.width = Math.min(ApexUi.dp(a, 340), screenW - ApexUi.dp(a, 24));
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			w.setAttributes(lp);
		}
		d.show();
	}

	private static View row(Activity a, Item it, Runnable click) {
		LinearLayout row = new LinearLayout(a);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(new ApexCardBackground());
		row.setPadding(ApexUi.dp(a, 10), ApexUi.dp(a, 10), ApexUi.dp(a, 14), ApexUi.dp(a, 10));
		row.setClickable(true);

		ImageView logo = new ImageView(a);
		logo.setImageResource(it.icon);
		int s = ApexUi.dp(a, 44);
		row.addView(logo, new LinearLayout.LayoutParams(s, s));

		TextView label = new TextView(a);
		label.setText(it.label);
		label.setTextSize(20);
		label.setTextColor(ApexTheme.textColor(a));
		ApexTheme.applyFont(label);
		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		tlp.leftMargin = ApexUi.dp(a, 14);
		row.addView(label, tlp);

		if (it.checked) {
			TextView mark = new TextView(a);
			mark.setText("✓");
			mark.setTextSize(22);
			mark.setTextColor(ApexTheme.pressedColor(a));
			row.addView(mark);
		} else {
			ImageView chevron = new ImageView(a);
			chevron.setImageResource(R.drawable.ic_apex_chevron);
			chevron.setColorFilter(ApexTheme.textColor(a));
			chevron.setAlpha(0.6f);
			int cs = ApexUi.dp(a, 22);
			row.addView(chevron, new LinearLayout.LayoutParams(cs, cs));
		}

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.topMargin = ApexUi.dp(a, 8);
		row.setLayoutParams(lp);
		row.setOnClickListener(v -> click.run());
		return row;
	}
}
