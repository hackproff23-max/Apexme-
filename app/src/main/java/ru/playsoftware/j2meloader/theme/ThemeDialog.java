package ru.playsoftware.j2meloader.theme;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.OutlinedTextView;
import yuku.ambilwarna.AmbilWarnaDialog;

/** Janela "Cores do Apexme": cores, fundos e fonte, cada opcao num cartao com logo. */
public final class ThemeDialog {

	public interface Picker {
		void pick(int target);
	}

	private ThemeDialog() {
	}

	public static void show(Activity activity, Picker picker) {
		float d = activity.getResources().getDisplayMetrics().density;
		int pad = (int) (d * 16);
		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad / 2, pad, pad / 2);

		final boolean[] changed = {false};
		final AlertDialog[] holder = new AlertDialog[1];

		addColorRow(root, activity, R.string.apex_color_text, ApexTheme.K_TEXT, ApexTheme.DEF_TEXT, changed);
		addColorRow(root, activity, R.string.apex_color_outline, ApexTheme.K_OUTLINE, ApexTheme.DEF_OUTLINE, changed);
		addColorRow(root, activity, R.string.apex_color_bg, ApexTheme.K_BG, ApexTheme.DEF_BG, changed);
		addColorRow(root, activity, R.string.apex_color_window, ApexTheme.K_WINDOW, ApexTheme.DEF_BG, changed);
		addColorRow(root, activity, R.string.apex_color_pressed, ApexTheme.K_PRESSED, ApexTheme.DEF_PRESSED, changed);

		addActionRow(root, activity, R.drawable.apexlogo_image, R.string.apex_import_app_bg, v -> {
			holder[0].dismiss();
			picker.pick(ApexTheme.TARGET_APP_BG);
		}, R.string.apex_remove, v -> {
			ApexTheme.clear(activity, ApexTheme.TARGET_APP_BG);
			changed[0] = true;
			holder[0].dismiss();
		});
		addActionRow(root, activity, R.drawable.apexlogo_window, R.string.apex_import_window_bg, v -> {
			holder[0].dismiss();
			picker.pick(ApexTheme.TARGET_WINDOW_BG);
		}, R.string.apex_remove, v -> {
			ApexTheme.clear(activity, ApexTheme.TARGET_WINDOW_BG);
			changed[0] = true;
			holder[0].dismiss();
		});
		addActionRow(root, activity, R.drawable.apexlogo_font, R.string.apex_import_font, v -> {
			holder[0].dismiss();
			picker.pick(ApexTheme.TARGET_FONT);
		}, R.string.apex_remove, v -> {
			ApexTheme.clear(activity, ApexTheme.TARGET_FONT);
			changed[0] = true;
			holder[0].dismiss();
		});
		addActionRow(root, activity, R.drawable.apexlogo_reset, R.string.apex_reset, v -> {
			ApexTheme.resetAll(activity);
			changed[0] = true;
			holder[0].dismiss();
		}, 0, null);

		ScrollView scroll = new ScrollView(activity);
		scroll.addView(root);

		AlertDialog dialog = new AlertDialog.Builder(activity)
				.setTitle(R.string.apex_colors_title)
				.setView(scroll)
				.setPositiveButton(R.string.apex_apply, null)
				.create();
		holder[0] = dialog;
		dialog.setOnDismissListener(di -> {
			if (changed[0]) {
				activity.recreate();
			}
		});
		dialog.show();
	}

	private static TextView label(Activity a, int textRes) {
		OutlinedTextView tv = new OutlinedTextView(a);
		tv.setText(textRes);
		tv.setTextSize(16);
		return tv;
	}

	/** Linha em forma de cartao: logo + conteudo. */
	private static LinearLayout card(Activity a, int iconRes) {
		float d = a.getResources().getDisplayMetrics().density;
		LinearLayout row = new LinearLayout(a);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(new ApexCardBackground());
		row.setPadding((int) (d * 10), (int) (d * 8), (int) (d * 14), (int) (d * 8));
		row.setClickable(true);

		ImageView logo = new ImageView(a);
		logo.setImageResource(iconRes);
		int size = (int) (d * 44);
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(size, size);
		llp.rightMargin = (int) (d * 12);
		row.addView(logo, llp);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.topMargin = (int) (d * 8);
		row.setLayoutParams(lp);
		return row;
	}

	private static void addColorRow(LinearLayout root, Activity a, int labelRes, String key, int def,
									boolean[] changed) {
		float d = a.getResources().getDisplayMetrics().density;
		LinearLayout row = card(a, R.drawable.apexlogo_colors);

		TextView tv = label(a, labelRes);
		row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		View swatch = new View(a);
		int size = (int) (d * 32);
		row.addView(swatch, new LinearLayout.LayoutParams(size, size));
		paintSwatch(swatch, ApexTheme.getColor(a, key, def), d);

		row.setOnClickListener(v -> {
			int current = ApexTheme.getColor(a, key, def);
			new AmbilWarnaDialog(a, current, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
				@Override
				public void onOk(AmbilWarnaDialog dialog, int color) {
					ApexTheme.setColor(a, key, color);
					paintSwatch(swatch, color, d);
					changed[0] = true;
				}

				@Override
				public void onCancel(AmbilWarnaDialog dialog) {
				}
			}).show();
		});
		root.addView(row);
	}

	private static void paintSwatch(View swatch, int color, float d) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(d * 16);
		g.setStroke((int) (d * 2), Color.GRAY);
		swatch.setBackground(g);
	}

	private static void addActionRow(LinearLayout root, Activity a, int iconRes, int labelRes,
									 View.OnClickListener onClick, int secondRes, View.OnClickListener onSecond) {
		float d = a.getResources().getDisplayMetrics().density;
		LinearLayout row = card(a, iconRes);

		TextView tv = label(a, labelRes);
		row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		row.setOnClickListener(onClick);

		if (onSecond != null) {
			int vp = (int) (d * 10);
			TextView second = label(a, secondRes);
			second.setPadding((int) (d * 12), vp, 0, vp);
			second.setOnClickListener(onSecond);
			row.addView(second);
		}
		root.addView(row);
	}
}
