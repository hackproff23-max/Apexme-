package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import javax.microedition.util.ActivityResultListener;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/**
 * Utilitarios de interface das janelas do Apexme.
 * Botoes, chaves e controles deslizantes saem no mesmo estilo da tela de Configuracoes:
 * cartao arredondado translucido + logo colorido + nome. Tudo em tamanho compacto.
 */
final class ApexUi {
	/** Fator aplicado aos tamanhos de letra pedidos pelas janelas (compacto). */
	private static final float TEXT_FACTOR = 0.8f;
	private static final float TEXT_MIN = 10f;
	/** Tamanho do logo dentro dos cartoes e da letra dos botoes/chaves. */
	private static final int LOGO_DP = 30;
	private static final float CARD_TEXT_SP = 13f;

	private static int nextCode = 0xA7E0;

	interface IntListener {
		void on(int value);
	}

	interface UriListener {
		void on(Uri uri);
	}

	interface BoolListener {
		void on(boolean value);
	}

	private ApexUi() {
	}

	static int dp(Context c, float v) {
		return (int) (c.getResources().getDisplayMetrics().density * v);
	}

	static LinearLayout column(Context c) {
		LinearLayout l = new LinearLayout(c);
		l.setOrientation(LinearLayout.VERTICAL);
		int p = dp(c, 10);
		l.setPadding(p, dp(c, 4), p, dp(c, 4));
		return l;
	}

	static TextView text(Context c, String s, float sp) {
		TextView t = new TextView(c);
		t.setText(s);
		t.setTextSize(Math.max(TEXT_MIN, sp * TEXT_FACTOR));
		t.setTextColor(ApexTheme.textColor(c));
		ApexTheme.applyFont(t);
		return t;
	}

	/** Titulo de grupo (acima dos cartoes). */
	static TextView section(Context c, String s) {
		TextView t = text(c, s, 17);
		t.setPadding(dp(c, 3), dp(c, 8), 0, dp(c, 3));
		return t;
	}

	private static boolean has(String s, String... keys) {
		for (String k : keys) {
			if (s.contains(k)) {
				return true;
			}
		}
		return false;
	}

	/** Escolhe o logo colorido (apexlogo_*) pelo nome da opcao. */
	static int iconFor(String label) {
		String s = label == null ? "" : label.toLowerCase(Locale.ROOT);
		if (has(s, "restaur", "remov", "limpar")) {
			return R.drawable.apexlogo_reset;
		}
		if (has(s, "shader", "libretro", "testar")) {
			return R.drawable.apexlogo_shaders;
		}
		if (has(s, "saturação", "brilho", "contraste", "cores")) {
			return R.drawable.apexlogo_colors;
		}
		if (has(s, "lcd", "opacidade")) {
			return R.drawable.apexlogo_shadow;
		}
		if (has(s, "imagem", "imagens")) {
			return R.drawable.apexlogo_image;
		}
		if (has(s, "tela", "resolu", "orienta")) {
			return R.drawable.apexlogo_screen;
		}
		if (has(s, "letras")) {
			return R.drawable.apexlogo_font;
		}
		if (has(s, "fast", "velocidade")) {
			return R.drawable.apexlogo_speed;
		}
		if (has(s, "tamanho")) {
			return R.drawable.apexlogo_outline;
		}
		return R.drawable.apexlogo_gamepad;
	}

	private static LinearLayout.LayoutParams cardParams(Context c) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.topMargin = dp(c, 3);
		lp.bottomMargin = dp(c, 3);
		return lp;
	}

	private static void cardPadding(Context c, View v) {
		v.setPadding(dp(c, 6), dp(c, 6), dp(c, 9), dp(c, 6));
	}

	/** Logo ja no tamanho compacto (os vetores sao 44dp, aqui viram LOGO_DP). */
	private static Drawable logo(Context c, int res) {
		Drawable d = ContextCompat.getDrawable(c, res);
		if (d == null) {
			return null;
		}
		d = d.mutate();
		int s = dp(c, LOGO_DP);
		d.setBounds(0, 0, s, s);
		return d;
	}

	static Button button(Context c, String label, View.OnClickListener l) {
		return button(c, iconFor(label), label, l);
	}

	static Button button(Context c, int icon, String label, View.OnClickListener l) {
		Button b = new Button(c);
		b.setText(label);
		b.setAllCaps(false);
		b.setTextSize(CARD_TEXT_SP);
		b.setTextColor(ApexTheme.textColor(c));
		b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
		b.setBackground(new ApexCardBackground());
		b.setStateListAnimator(null);
		b.setMinHeight(0);
		b.setMinimumHeight(0);
		cardPadding(c, b);
		b.setCompoundDrawablePadding(dp(c, 8));
		b.setCompoundDrawablesRelative(logo(c, icon), null, null, null);
		b.setOnClickListener(l);
		ApexTheme.applyFont(b);
		b.setLayoutParams(cardParams(c));
		return b;
	}

	/** Coloca cartoes lado a lado (mantem as margens de cada um). */
	static LinearLayout row(Context c, View... views) {
		LinearLayout r = new LinearLayout(c);
		r.setOrientation(LinearLayout.HORIZONTAL);
		for (int i = 0; i < views.length; i++) {
			View v = views[i];
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
					LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			ViewGroup.LayoutParams old = v.getLayoutParams();
			if (old instanceof LinearLayout.LayoutParams) {
				lp.topMargin = ((LinearLayout.LayoutParams) old).topMargin;
				lp.bottomMargin = ((LinearLayout.LayoutParams) old).bottomMargin;
			}
			if (i > 0) {
				lp.leftMargin = dp(c, 6);
			}
			r.addView(v, lp);
		}
		return r;
	}

	static View slider(Context c, String label, int min, int max, int cur, String suffix, IntListener l) {
		return slider(c, iconFor(label), label, min, max, cur, suffix, l);
	}

	static View slider(Context c, int icon, String label, int min, int max, int cur, String suffix,
					   IntListener l) {
		LinearLayout card = new LinearLayout(c);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER_VERTICAL);
		card.setBackground(new ApexCardBackground());
		cardPadding(c, card);

		ImageView img = new ImageView(c);
		img.setImageDrawable(logo(c, icon));
		int s = dp(c, LOGO_DP);
		card.addView(img, new LinearLayout.LayoutParams(s, s));

		LinearLayout col = new LinearLayout(c);
		col.setOrientation(LinearLayout.VERTICAL);
		TextView t = text(c, label + ": " + cur + suffix, 16);
		SeekBar sb = new SeekBar(c);
		sb.setMax(max - min);
		sb.setProgress(Math.max(0, Math.min(max - min, cur - min)));
		sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
				t.setText(label + ": " + (min + p) + suffix);
				if (fromUser) {
					l.on(min + p);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar s) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar s) {
			}
		});
		col.addView(t);
		col.addView(sb);
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		clp.leftMargin = dp(c, 8);
		card.addView(col, clp);
		card.setLayoutParams(cardParams(c));
		return card;
	}

	static SwitchCompat toggle(Context c, String label, boolean checked, BoolListener l) {
		return toggle(c, iconFor(label), label, checked, l);
	}

	static SwitchCompat toggle(Context c, int icon, String label, boolean checked, BoolListener l) {
		SwitchCompat sw = new SwitchCompat(c);
		sw.setText(label);
		sw.setTextSize(CARD_TEXT_SP);
		sw.setTextColor(ApexTheme.textColor(c));
		sw.setChecked(checked);
		sw.setOnCheckedChangeListener((b, v) -> l.on(v));
		sw.setBackground(new ApexCardBackground());
		cardPadding(c, sw);
		sw.setCompoundDrawablePadding(dp(c, 8));
		sw.setCompoundDrawablesRelative(logo(c, icon), null, null, null);
		ApexTheme.applyFont(sw);
		sw.setLayoutParams(cardParams(c));
		return sw;
	}

	/** Abre o seletor de arquivos do sistema e devolve o Uri escolhido. */
	static void pick(Activity a, String mime, UriListener l) {
		final int code = nextCode++;
		ActivityResultListener[] holder = new ActivityResultListener[1];
		holder[0] = (req, res, data) -> {
			if (req != code) {
				return;
			}
			ContextHolder.removeActivityResultListener(holder[0]);
			if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
				l.on(data.getData());
			}
		};
		ContextHolder.addActivityResultListener(holder[0]);
		Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		i.addCategory(Intent.CATEGORY_OPENABLE);
		i.setType(mime);
		try {
			a.startActivityForResult(i, code);
		} catch (Exception e) {
			ContextHolder.removeActivityResultListener(holder[0]);
		}
	}
}
