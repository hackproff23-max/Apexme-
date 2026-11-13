package ru.playsoftware.j2meloader.apex;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import javax.microedition.lcdui.ApexScreen;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Janela Tela: cada escolha aplica imediatamente, sem botao Salvar. */
public final class ScreenDialog {
	private static final int[][] PRESETS = {
			{0, 0}, {240, 320}, {320, 240}, {360, 640}, {640, 360}, {480, 800}, {800, 480}};
	private static final int[] ORIENT = {
			ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
			ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE};
	private static final String[] ORIENT_NAMES = {"Padrão", "Automática", "Retrato", "Paisagem"};
	private static final int MIN_SIDE = 32;
	private static final int MAX_SIDE = 4096;

	private ScreenDialog() {
	}

	private static Canvas canvas(MicroActivity a) {
		return a.getCurrent() instanceof Canvas ? (Canvas) a.getCurrent() : null;
	}

	public static void show(MicroActivity a) {
		if (a.isFinishing()) {
			return;
		}
		android.content.SharedPreferences prefs =
				a.getSharedPreferences("apex_screen", Context.MODE_PRIVATE);
		int[] cur = {
				prefs.getInt("width", Math.max(0, Displayable.getVirtualWidth())),
				prefs.getInt("height", Math.max(0, Displayable.getVirtualHeight())),
				prefs.getInt("orientation", 0)};
		int req = a.getRequestedOrientation();
		for (int i = 0; i < ORIENT.length; i++) {
			if (ORIENT[i] == req) {
				cur[2] = i;
			}
		}
		Canvas first = canvas(a);
		boolean[] fs = {prefs.getBoolean("fullscreen",
				first != null && ApexScreen.isFullscreen(first))};

		Dialog d = new Dialog(a);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		LinearLayout root = ApexUi.column(a);
		root.setBackgroundColor(Color.argb(55, 0, 0, 0));

		// resolucao
		root.addView(label(a, "Resolução (largura × altura, 0 = automática)", 14));
		EditText ew = field(a, cur[0]);
		EditText eh = field(a, cur[1]);
		View.OnFocusChangeListener resolutionListener = (v, hasFocus) -> {
			if (!hasFocus) {
				int nw = parseSide(ew.getText());
				int nh = parseSide(eh.getText());
				if (nw < 0 || nh < 0) {
					((EditText) v).setError("Use 0 ou 32–4096");
					return;
				}
				cur[0] = nw;
				cur[1] = nh;
				applyNow(a, prefs, cur, fs[0]);
			}
		};
		ew.setOnFocusChangeListener(resolutionListener);
		eh.setOnFocusChangeListener(resolutionListener);
		LinearLayout resRow = new LinearLayout(a);
		resRow.setOrientation(LinearLayout.HORIZONTAL);
		resRow.setGravity(Gravity.CENTER_VERTICAL);
		resRow.addView(ew, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		TextView x = label(a, "  ×  ", 18);
		resRow.addView(x);
		resRow.addView(eh, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		root.addView(resRow);

		LinearLayout presets = new LinearLayout(a);
		presets.setOrientation(LinearLayout.HORIZONTAL);
		for (int[] p : PRESETS) {
			TextView chip = chip(a, p[0] == 0 ? "Auto" : p[0] + "×" + p[1], false);
			chip.setOnClickListener(v -> {
				ew.setText(String.valueOf(p[0]));
				eh.setText(String.valueOf(p[1]));
				cur[0] = p[0];
				cur[1] = p[1];
				ew.clearFocus();
				eh.clearFocus();
				applyNow(a, prefs, cur, fs[0]);
			});
			presets.addView(chip, chipParams(a, false));
		}
		HorizontalScrollView hs = new HorizontalScrollView(a);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(presets);
		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		hlp.topMargin = ApexUi.dp(a, 8);
		root.addView(hs, hlp);

		// tela cheia
		SwitchCompat sw = ApexUi.toggle(a, "Tela cheia", fs[0], v -> {
			fs[0] = v;
			applyNow(a, prefs, cur, fs[0]);
		});
		sw.setShadowLayer(ApexUi.dp(a, 3), 0, 0, Color.BLACK);
		root.addView(sw);

		// orientacao
		root.addView(label(a, "Orientação da tela", 14));
		LinearLayout orow = new LinearLayout(a);
		orow.setOrientation(LinearLayout.HORIZONTAL);
		TextView[] oc = new TextView[ORIENT.length];
		for (int i = 0; i < ORIENT.length; i++) {
			final int idx = i;
			oc[i] = chip(a, ORIENT_NAMES[i], i == cur[2]);
			oc[i].setOnClickListener(v -> {
				cur[2] = idx;
				for (int k = 0; k < oc.length; k++) {
					mark(a, oc[k], k == idx);
				}
				applyNow(a, prefs, cur, fs[0]);
			});
			orow.addView(oc[i], chipParams(a, true));
		}
		LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		olp.topMargin = ApexUi.dp(a, 4);
		root.addView(orow, olp);

		ScrollView sv = new ScrollView(a) {
			@Override
			protected void onMeasure(int w, int h) {
				int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
				super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
			}
		};
		sv.addView(root);
		d.setContentView(sv);
		d.setCanceledOnTouchOutside(false);
		Window w = d.getWindow();
		if (w != null) {
			w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
			w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
					| WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
			w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
			WindowManager.LayoutParams lp = w.getAttributes();
			lp.width = WindowManager.LayoutParams.MATCH_PARENT;
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			lp.dimAmount = 0f;
			w.setAttributes(lp);
		}
		d.show();
	}

	private static void applyNow(MicroActivity a,
			android.content.SharedPreferences prefs, int[] values, boolean fullscreen) {
		prefs.edit().putInt("width", values[0]).putInt("height", values[1])
				.putInt("orientation", values[2]).putBoolean("fullscreen", fullscreen).apply();
		a.setRequestedOrientation(ORIENT[values[2]]);
		Canvas c = canvas(a);
		if (c != null) {
			ApexScreen.apply(c, values[0], values[1], fullscreen);
		}
	}

	private static int parseSide(CharSequence s) {
		String t = s == null ? "" : s.toString().trim();
		if (t.isEmpty()) {
			return 0;
		}
		try {
			int v = Integer.parseInt(t);
			if (v == 0) {
				return 0;
			}
			return v >= MIN_SIDE && v <= MAX_SIDE ? v : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static TextView label(MicroActivity a, String s, float sp) {
		TextView t = ApexUi.text(a, s, sp);
		t.setShadowLayer(ApexUi.dp(a, 3), 0, 0, Color.BLACK);
		return t;
	}

	private static EditText field(MicroActivity a, int value) {
		EditText e = new EditText(a);
		e.setInputType(InputType.TYPE_CLASS_NUMBER);
		e.setSingleLine(true);
		e.setText(String.valueOf(Math.max(0, value)));
		e.setTextSize(16);
		e.setTextColor(ApexTheme.textColor(a));
		e.setGravity(Gravity.CENTER);
		e.setSelectAllOnFocus(true);
		e.setBackgroundColor(Color.argb(90, 0, 0, 0));
		int p = ApexUi.dp(a, 8);
		e.setPadding(p, p, p, p);
		ApexTheme.applyFont(e);
		e.setShadowLayer(ApexUi.dp(a, 3), 0, 0, Color.BLACK);
		return e;
	}

	private static TextView chip(MicroActivity a, String s, boolean on) {
		TextView t = label(a, s, 15);
		int ph = ApexUi.dp(a, 12);
		int pv = ApexUi.dp(a, 8);
		t.setPadding(ph, pv, ph, pv);
		t.setGravity(Gravity.CENTER);
		mark(a, t, on);
		return t;
	}

	private static void mark(MicroActivity a, TextView t, boolean on) {
		int pc = ApexTheme.pressedColor(a);
		t.setBackgroundColor(on ? Color.argb(150, Color.red(pc), Color.green(pc), Color.blue(pc))
				: Color.argb(80, 0, 0, 0));
	}

	private static LinearLayout.LayoutParams chipParams(MicroActivity a, boolean weighted) {
		LinearLayout.LayoutParams lp = weighted
				? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
				: new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = ApexUi.dp(a, 6);
		return lp;
	}
}
