package ru.playsoftware.j2meloader.apex;

import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import java.io.File;

import javax.microedition.lcdui.ApexScreen;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/**
 * Janela Tela: a resolucao e aplicada ao vivo enquanto voce digita (ou toca num preset),
 * e so e gravada no perfil do jogo ao tocar em Salvar. Cancelar volta ao que estava antes.
 */
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
	private static final long PREVIEW_DELAY_MS = 350;

	private ScreenDialog() {
	}

	private static Canvas canvas(MicroActivity a) {
		return a.getCurrent() instanceof Canvas ? (Canvas) a.getCurrent() : null;
	}

	public static void show(MicroActivity a) {
		if (a.isFinishing()) {
			return;
		}
		// valores atuais (o que esta na tela agora)
		int[] cur = {
				Math.max(0, Displayable.getVirtualWidth()),
				Math.max(0, Displayable.getVirtualHeight()),
				0};
		int req = a.getRequestedOrientation();
		for (int i = 0; i < ORIENT.length; i++) {
			if (ORIENT[i] == req) {
				cur[2] = i;
			}
		}
		Canvas first = canvas(a);
		boolean[] fs = {first != null && ApexScreen.isFullscreen(first)};
		// copia para o Cancelar
		final int[] orig = cur.clone();
		final boolean origFs = fs[0];
		final boolean[] saved = {false};

		Dialog d = new Dialog(a);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		LinearLayout root = ApexUi.column(a);
		root.setBackgroundColor(Color.argb(55, 0, 0, 0));
		Handler handler = new Handler(Looper.getMainLooper());

		// ---- resolucao ----
		root.addView(label(a, "Resolução (largura × altura, 0 = automática)", 14));
		EditText ew = field(a, cur[0]);
		EditText eh = field(a, cur[1]);

		// aplica ao vivo, com um pequeno atraso para nao redesenhar a cada tecla
		Runnable preview = () -> {
			int nw = parseSide(ew.getText());
			int nh = parseSide(eh.getText());
			ew.setError(nw < 0 ? "Use 0 ou 32–4096" : null);
			eh.setError(nh < 0 ? "Use 0 ou 32–4096" : null);
			if (nw < 0 || nh < 0) {
				return;
			}
			if (nw != cur[0] || nh != cur[1]) {
				cur[0] = nw;
				cur[1] = nh;
				applyPreview(a, cur, fs[0]);
			}
		};
		TextWatcher watcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int st, int c, int af) {
			}

			@Override
			public void onTextChanged(CharSequence s, int st, int b, int c) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				handler.removeCallbacks(preview);
				handler.postDelayed(preview, PREVIEW_DELAY_MS);
			}
		};
		ew.addTextChangedListener(watcher);
		eh.addTextChangedListener(watcher);

		LinearLayout resRow = new LinearLayout(a);
		resRow.setOrientation(LinearLayout.HORIZONTAL);
		resRow.setGravity(Gravity.CENTER_VERTICAL);
		resRow.addView(ew, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		resRow.addView(label(a, "  ×  ", 18));
		resRow.addView(eh, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		root.addView(resRow);

		LinearLayout presets = new LinearLayout(a);
		presets.setOrientation(LinearLayout.HORIZONTAL);
		for (int[] p : PRESETS) {
			TextView chip = chip(a, p[0] == 0 ? "Auto" : p[0] + "×" + p[1], false);
			chip.setOnClickListener(v -> {
				ew.setText(String.valueOf(p[0]));
				eh.setText(String.valueOf(p[1]));
				handler.removeCallbacks(preview);
				ew.clearFocus();
				eh.clearFocus();
				preview.run();
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

		// ---- tela cheia ----
		SwitchCompat sw = ApexUi.toggle(a, "Tela cheia", fs[0], v -> {
			fs[0] = v;
			applyPreview(a, cur, fs[0]);
		});
		sw.setShadowLayer(ApexUi.dp(a, 3), 0, 0, Color.BLACK);
		root.addView(sw);

		// ---- orientacao ----
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
				applyPreview(a, cur, fs[0]);
			});
			orow.addView(oc[i], chipParams(a, true));
		}
		LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		olp.topMargin = ApexUi.dp(a, 4);
		root.addView(orow, olp);

		// ---- rodape fixo: Cancelar / Salvar ----
		LinearLayout footer = new LinearLayout(a);
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setBackgroundColor(Color.argb(110, 0, 0, 0));
		int fp = ApexUi.dp(a, 8);
		footer.setPadding(fp, fp, fp, fp);

		TextView cancel = chip(a, "Cancelar", false);
		cancel.setOnClickListener(v -> d.dismiss()); // o dismiss reverte
		footer.addView(cancel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		TextView save = chip(a, "Salvar", true);
		save.setOnClickListener(v -> {
			handler.removeCallbacks(preview);
			int nw = parseSide(ew.getText());
			int nh = parseSide(eh.getText());
			if (nw < 0 || nh < 0) {
				ew.setError(nw < 0 ? "Use 0 ou 32–4096" : null);
				eh.setError(nh < 0 ? "Use 0 ou 32–4096" : null);
				return;
			}
			cur[0] = nw;
			cur[1] = nh;
			applyPreview(a, cur, fs[0]);
			if (persist(a, cur, fs[0])) {
				saved[0] = true;
				Toast.makeText(a, "Tela salva", Toast.LENGTH_SHORT).show();
				d.dismiss();
			} else {
				Toast.makeText(a, "Não foi possível salvar o perfil do jogo", Toast.LENGTH_LONG).show();
			}
		});
		LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		slp.leftMargin = ApexUi.dp(a, 8);
		footer.addView(save, slp);

		ScrollView sv = new ScrollView(a) {
			@Override
			protected void onMeasure(int w, int h) {
				int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
				super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
			}
		};
		sv.addView(root);

		LinearLayout outer = new LinearLayout(a);
		outer.setOrientation(LinearLayout.VERTICAL);
		outer.addView(sv, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		outer.addView(footer, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		d.setContentView(outer);
		d.setCanceledOnTouchOutside(false);
		// Sair sem salvar (Cancelar / botao voltar): restaura o que estava antes.
		d.setOnDismissListener(x -> {
			handler.removeCallbacks(preview);
			if (!saved[0]) {
				applyPreview(a, orig, origFs);
			}
		});
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

	private static void applyPreview(MicroActivity a, int[] values, boolean fullscreen) {
		a.setRequestedOrientation(ORIENT[values[2]]);
		Canvas c = canvas(a);
		if (c != null) {
			ApexScreen.apply(c, values[0], values[1], fullscreen);
		}
	}

	/** Pasta do perfil do jogo (a mesma que o MicroLoader le ao iniciar). */
	private static File profileDir(MicroActivity a) {
		try {
			String appPath;
			if (BuildConfig.FULL_EMULATOR) {
				Uri data = a.getIntent().getData();
				if (data == null) {
					return null;
				}
				appPath = data.toString();
			} else {
				appPath = a.getApplicationInfo().dataDir + "/files/converted/midlet";
			}
			File appDir = new File(appPath);
			File converted = appDir.getParentFile();
			if (converted == null || converted.getParent() == null) {
				return null;
			}
			return new File(converted.getParent() + Config.MIDLET_CONFIGS_DIR + appDir.getName());
		} catch (Throwable e) {
			return null;
		}
	}

	/** Grava no perfil do jogo: e daqui que o jogo le a resolucao na proxima abertura. */
	private static boolean persist(MicroActivity a, int[] v, boolean fullscreen) {
		File dir = profileDir(a);
		if (dir == null) {
			return false;
		}
		ProfileModel p = ProfilesManager.loadConfig(dir);
		if (p == null) {
			return false;
		}
		p.screenWidth = v[0];
		p.screenHeight = v[1];
		p.orientation = v[2];
		p.forceFullscreen = fullscreen;
		return ProfilesManager.saveConfig(p);
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
