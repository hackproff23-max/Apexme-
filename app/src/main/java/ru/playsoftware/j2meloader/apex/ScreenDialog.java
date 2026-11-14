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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.theme.ApexWindowBackground;

/**
 * Janela Tela no estilo das Configuracoes (cartoes com logo). A resolucao e aplicada ao vivo
 * enquanto voce digita (ou toca num preset) e so e gravada no perfil do jogo ao tocar em Salvar.
 * Cancelar volta ao que estava antes. O painel fica de lado (ou embaixo, em pe) para o jogo
 * continuar visivel durante a pre-visualizacao.
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
		final int[] orig = cur.clone();
		final boolean origFs = fs[0];
		final boolean[] saved = {false};

		Dialog d = new Dialog(a);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Handler handler = new Handler(Looper.getMainLooper());

		LinearLayout root = new LinearLayout(a);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = ApexUi.dp(a, 16);
		root.setPadding(pad, pad, pad, pad);

		TextView title = ApexUi.text(a, "Tela", 24);
		title.setPadding(ApexUi.dp(a, 4), 0, 0, ApexUi.dp(a, 6));
		root.addView(title);

		// ---- cartao: resolucao ----
		LinearLayout resCard = card(a);
		resCard.addView(header(a, R.drawable.apexlogo_screen, "Resolução (0 = automática)"));
		EditText ew = field(a, cur[0]);
		EditText eh = field(a, cur[1]);

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

		LinearLayout fields = new LinearLayout(a);
		fields.setOrientation(LinearLayout.HORIZONTAL);
		fields.setGravity(Gravity.CENTER_VERTICAL);
		fields.addView(ew, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		TextView times = ApexUi.text(a, "  ×  ", 18);
		fields.addView(times);
		fields.addView(eh, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		flp.topMargin = ApexUi.dp(a, 8);
		resCard.addView(fields, flp);

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
		resCard.addView(hs, hlp);

		// ---- cartao: tela cheia ----
		SwitchCompat sw = ApexUi.toggle(a, R.drawable.apexlogo_screen, "Tela cheia", fs[0], v -> {
			fs[0] = v;
			applyPreview(a, cur, fs[0]);
		});

		// ---- cartao: orientacao ----
		LinearLayout oriCard = card(a);
		oriCard.addView(header(a, R.drawable.apexlogo_screen, "Orientação da tela"));
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
			orow.addView(oc[i], chipParams(a, false));
		}
		HorizontalScrollView ohs = new HorizontalScrollView(a);
		ohs.setHorizontalScrollBarEnabled(false);
		ohs.addView(orow);
		LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		olp.topMargin = ApexUi.dp(a, 8);
		oriCard.addView(ohs, olp);

		LinearLayout body = new LinearLayout(a);
		body.setOrientation(LinearLayout.VERTICAL);
		body.addView(resCard);
		body.addView(sw);
		body.addView(oriCard);

		boolean landscape = a.getResources().getDisplayMetrics().widthPixels
				> a.getResources().getDisplayMetrics().heightPixels;
		int screenH = a.getResources().getDisplayMetrics().heightPixels;
		ScrollView sv = new ScrollView(a) {
			@Override
			protected void onMeasure(int w, int h) {
				int max = (int) (screenH * (landscape ? 0.62f : 0.42f));
				super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
			}
		};
		sv.setVerticalScrollBarEnabled(false);
		sv.addView(body);
		root.addView(sv);

		// ---- rodape fixo: Cancelar / Salvar ----
		android.widget.Button cancel = ApexUi.button(a, R.drawable.apexlogo_exit, "Cancelar", v -> d.dismiss());
		android.widget.Button save = ApexUi.button(a, R.drawable.apexlogo_save, "Salvar", v -> {
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
		root.addView(ApexUi.row(a, cancel, save));

		FrameLayout frame = new FrameLayout(a);
		frame.setBackground(new ApexWindowBackground());
		frame.addView(root, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
		d.setContentView(frame);
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
			WindowManager.LayoutParams lp = w.getAttributes();
			int screenW = a.getResources().getDisplayMetrics().widthPixels;
			if (landscape) {
				w.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
				lp.width = Math.min(ApexUi.dp(a, 360), (int) (screenW * 0.5f));
				lp.x = ApexUi.dp(a, 8);
			} else {
				w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
				lp.width = screenW - ApexUi.dp(a, 16);
			}
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

	/** Cartao arredondado translucido (mesmo fundo das Configuracoes). */
	private static LinearLayout card(MicroActivity a) {
		LinearLayout c = new LinearLayout(a);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setBackground(new ApexCardBackground());
		c.setPadding(ApexUi.dp(a, 10), ApexUi.dp(a, 10), ApexUi.dp(a, 14), ApexUi.dp(a, 10));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.topMargin = ApexUi.dp(a, 4);
		lp.bottomMargin = ApexUi.dp(a, 4);
		c.setLayoutParams(lp);
		return c;
	}

	/** Linha de cabecalho do cartao: logo colorido + nome. */
	private static View header(MicroActivity a, int icon, String label) {
		LinearLayout h = new LinearLayout(a);
		h.setOrientation(LinearLayout.HORIZONTAL);
		h.setGravity(Gravity.CENTER_VERTICAL);
		ImageView logo = new ImageView(a);
		logo.setImageResource(icon);
		int s = ApexUi.dp(a, 44);
		h.addView(logo, new LinearLayout.LayoutParams(s, s));
		TextView t = ApexUi.text(a, label, 16);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		lp.leftMargin = ApexUi.dp(a, 12);
		h.addView(t, lp);
		return h;
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
		e.setBackgroundColor(Color.argb(60, 0, 0, 0));
		int p = ApexUi.dp(a, 8);
		e.setPadding(p, p, p, p);
		ApexTheme.applyFont(e);
		return e;
	}

	private static TextView chip(MicroActivity a, String s, boolean on) {
		TextView t = ApexUi.text(a, s, 15);
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
				: Color.argb(60, 0, 0, 0));
	}

	private static LinearLayout.LayoutParams chipParams(MicroActivity a, boolean weighted) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = ApexUi.dp(a, 6);
		return lp;
	}
}
