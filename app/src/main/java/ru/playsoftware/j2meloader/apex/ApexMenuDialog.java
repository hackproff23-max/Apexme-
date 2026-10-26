package ru.playsoftware.j2meloader.apex;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Menu lateral do Apexme: Sair, Shaders e Teclado virtual, cada um com seu icone. */
public final class ApexMenuDialog {
	private ApexMenuDialog() {
	}

	public static void show(MicroActivity a) {
		if (a.isFinishing()) {
			return;
		}
		Dialog d = new Dialog(a);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		LinearLayout root = new LinearLayout(a);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(ApexTheme.bgColor(a) | 0xFF000000);
		int pad = ApexUi.dp(a, 8);
		root.setPadding(pad, pad * 2, pad, pad * 2);

		root.addView(item(a, R.drawable.ic_apex_exit, a.getString(R.string.exit), () -> {
			d.dismiss();
			a.showExitConfirmation();
		}));
		root.addView(item(a, R.drawable.ic_apex_shaders, a.getString(R.string.apex_shaders), () -> {
			d.dismiss();
			ShadersDialog.show(a);
		}));
		root.addView(item(a, R.drawable.ic_apex_keyboard,
				a.getString(R.string.PREF_VIRTUAL_KEYBOARD_OPTIONS), () -> {
					d.dismiss();
					VkDialog.show(a);
				}));
		d.setContentView(root);
		Window w = d.getWindow();
		if (w != null) {
			w.setBackgroundDrawable(new ColorDrawable(0xFF000000));
			w.setGravity(Gravity.START | Gravity.TOP);
			WindowManager.LayoutParams lp = w.getAttributes();
			lp.width = ApexUi.dp(a, 260);
			lp.height = WindowManager.LayoutParams.MATCH_PARENT;
			w.setAttributes(lp);
		}
		d.show();
	}

	private static View item(MicroActivity a, int icon, String label, Runnable click) {
		LinearLayout row = new LinearLayout(a);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		int p = ApexUi.dp(a, 14);
		row.setPadding(p, p, p, p);
		row.setBackgroundResource(R.drawable.apex_pressed);
		ImageView iv = new ImageView(a);
		iv.setImageResource(icon);
		iv.setColorFilter(ApexTheme.outlineColor(a));
		int s = ApexUi.dp(a, 28);
		row.addView(iv, new LinearLayout.LayoutParams(s, s));
		TextView t = new TextView(a);
		t.setText(label);
		t.setTextSize(18);
		t.setTextColor(ApexTheme.textColor(a));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.leftMargin = ApexUi.dp(a, 16);
		row.addView(t, lp);
		row.setOnClickListener(v -> click.run());
		return row;
	}
}
