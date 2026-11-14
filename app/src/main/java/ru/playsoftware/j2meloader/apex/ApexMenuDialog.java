package ru.playsoftware.j2meloader.apex;

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

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.theme.ApexWindowBackground;

/**
 * Janela de configuracoes do Apexme: titulo e um cartao para cada opcao (logo colorido, nome e seta).
 * O fundo da janela continua sendo a imagem de janela do app (ApexWindowBackground); os cartoes
 * ficam translucidos por cima dela: brancos no tema claro, pretos no escuro.
 */
public final class ApexMenuDialog {
	private ApexMenuDialog() {
	}

	public static void show(MicroActivity a) {
		if (a.isFinishing()) {
			return;
		}
		Dialog d = new Dialog(a);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);

		LinearLayout list = new LinearLayout(a);
		list.setOrientation(LinearLayout.VERTICAL);
		int pad = ApexUi.dp(a, 16);
		list.setPadding(pad, pad, pad, pad);

		TextView title = new TextView(a);
		title.setText("Configurações");
		title.setTextSize(24);
		title.setTextColor(ApexTheme.textColor(a));
		title.setPadding(ApexUi.dp(a, 4), 0, 0, ApexUi.dp(a, 6));
		ApexTheme.applyFont(title);
		list.addView(title);

		list.addView(item(a, R.drawable.apexlogo_exit, a.getString(R.string.exit), () -> {
			d.dismiss();
			a.showExitConfirmation();
		}));
		list.addView(item(a, R.drawable.apexlogo_shaders, a.getString(R.string.apex_shaders), () -> {
			d.dismiss();
			ShadersDialog.show(a);
		}));
		list.addView(item(a, R.drawable.apexlogo_gamepad,
				a.getString(R.string.PREF_VIRTUAL_KEYBOARD_OPTIONS), () -> {
					d.dismiss();
					VkDialog.show(a);
				}));
		list.addView(item(a, R.drawable.apexlogo_screen, "Tela", () -> {
			d.dismiss();
			ScreenDialog.show(a);
		}));
		list.addView(item(a, R.drawable.apexlogo_palette, "Tema do controle", () -> {
			d.dismiss();
			ApexControlTheme.showGame(a);
		}));

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
			w.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
			WindowManager.LayoutParams lp = w.getAttributes();
			int screenW = a.getResources().getDisplayMetrics().widthPixels;
			lp.width = Math.min(ApexUi.dp(a, 320), screenW - ApexUi.dp(a, 16));
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			lp.x = ApexUi.dp(a, 8);
			w.setAttributes(lp);
		}
		d.show();
	}

	private static View item(MicroActivity a, int icon, String label, Runnable click) {
		LinearLayout row = new LinearLayout(a);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(new ApexCardBackground());
		row.setPadding(ApexUi.dp(a, 10), ApexUi.dp(a, 10), ApexUi.dp(a, 14), ApexUi.dp(a, 10));
		row.setClickable(true);

		ImageView logo = new ImageView(a);
		logo.setImageResource(icon);
		int s = ApexUi.dp(a, 44);
		row.addView(logo, new LinearLayout.LayoutParams(s, s));

		TextView t = new TextView(a);
		t.setText(label);
		t.setTextSize(20);
		t.setTextColor(ApexTheme.textColor(a));
		ApexTheme.applyFont(t);
		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		tlp.leftMargin = ApexUi.dp(a, 14);
		row.addView(t, tlp);

		ImageView chevron = new ImageView(a);
		chevron.setImageResource(R.drawable.ic_apex_chevron);
		chevron.setColorFilter(ApexTheme.textColor(a));
		chevron.setAlpha(0.6f);
		int cs = ApexUi.dp(a, 22);
		row.addView(chevron, new LinearLayout.LayoutParams(cs, cs));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.topMargin = ApexUi.dp(a, 8);
		row.setLayoutParams(lp);
		row.setOnClickListener(v -> click.run());
		return row;
	}
}
