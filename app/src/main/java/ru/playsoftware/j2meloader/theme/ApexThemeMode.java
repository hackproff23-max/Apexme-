package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.CheckedTextViewCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.WeakHashMap;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.apex.ImportStore;

/**
 * Tema claro / escuro do Apexme.
 * Claro: textos, icones e controles pretos sobre fundo branco. Escuro: o inverso.
 * O modo e deduzido das cores salvas (texto e janela), entao "Restaurar padrao" volta ao original.
 */
public final class ApexThemeMode {
	public static final int MODE_CUSTOM = 0;
	public static final int MODE_LIGHT = 1;
	public static final int MODE_DARK = 2;

	private static final int BLACK = 0xFF000000;
	private static final int WHITE = 0xFFFFFFFF;
	/** Mesmas chaves usadas por ApexTheme para o fundo do app e o fundo das janelas. */
	private static final String PREF_APP_BG = "apex_app_bg_file";
	private static final String PREF_WIN_BG = "apex_window_bg_file";

	private static final Map<View, Integer> APPLIED = new WeakHashMap<>();

	private ApexThemeMode() {
	}

	/** Modo atual, deduzido das cores salvas. */
	public static int current(Context c) {
		int text = ApexTheme.textColor(c);
		int win = ApexTheme.windowColor(c);
		if (text == BLACK && win == WHITE) {
			return MODE_LIGHT;
		}
		if (text == WHITE && win == BLACK) {
			return MODE_DARK;
		}
		return MODE_CUSTOM;
	}

	/** Cor dos icones e controles no modo atual, ou 0 se o tema nao e claro/escuro. */
	static int iconTint(Context c) {
		int m = current(c);
		return m == MODE_LIGHT ? BLACK : m == MODE_DARK ? WHITE : 0;
	}

	/** Aplica o modo: cores, fundo do app e fundo das janelas. O chamador deve recriar a tela. */
	public static boolean apply(Context c, int mode) {
		Context app = c.getApplicationContext();
		if (mode != MODE_LIGHT && mode != MODE_DARK) {
			ApexTheme.resetAll(app);
			return true;
		}
		boolean light = mode == MODE_LIGHT;
		// tira fundos importados antes (apaga os arquivos em uso)
		ApexTheme.clear(app, ApexTheme.TARGET_APP_BG);
		ApexTheme.clear(app, ApexTheme.TARGET_WINDOW_BG);

		int res = light ? R.drawable.apex_theme_light : R.drawable.apex_theme_dark;
		String name = light ? "tema_claro" : "tema_escuro";
		File dir = ImportStore.dir(ImportStore.FUNDOS);
		File appFile = new File(dir, "app_bg_" + name + ".jpg");
		File winFile = new File(dir, "window_bg_" + name + ".jpg");
		boolean ok = copyRes(app, res, appFile) && copyRes(app, res, winFile);
		if (ok) {
			SharedPreferences.Editor e = PreferenceManager.getDefaultSharedPreferences(app).edit();
			e.putString(PREF_APP_BG, appFile.getPath());
			e.putString(PREF_WIN_BG, winFile.getPath());
			e.apply();
		}

		int text = light ? BLACK : WHITE;
		int back = light ? WHITE : BLACK;
		ApexTheme.setColor(app, ApexTheme.K_OUTLINE, back);
		ApexTheme.setColor(app, ApexTheme.K_BG, back);
		ApexTheme.setColor(app, ApexTheme.K_WINDOW, back);
		ApexTheme.setColor(app, ApexTheme.K_SHADOW_COLOR, back);
		ApexTheme.setColor(app, ApexTheme.K_TEXT, text);
		ImportStore.sync();
		return ok;
	}

	private static boolean copyRes(Context c, int res, File out) {
		try (InputStream in = c.getResources().openRawResource(res);
			 OutputStream os = new FileOutputStream(out)) {
			byte[] buf = new byte[16 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/** Textos dentro do jogo (MicroActivity) pertencem ao midlet: nao mexemos neles. */
	static boolean inMidlet(Context c) {
		while (c instanceof ContextWrapper) {
			if (c instanceof javax.microedition.shell.MicroActivity) {
				return true;
			}
			c = ((ContextWrapper) c).getBaseContext();
		}
		return false;
	}

	/** Deixa visivel no tema claro/escuro: cor do texto, icones, caixas, chaves, campos e barras. */
	static void tintView(View v, int tint) {
		if (v instanceof TextView) {
			TextView tv = (TextView) v;
			boolean plainButton = tv instanceof Button && !(tv instanceof CompoundButton);
			if (!plainButton && tv.getCurrentTextColor() != tint) {
				tv.setTextColor(tint);
			}
		}
		Integer done = APPLIED.get(v);
		if (done != null && done == tint) {
			return;
		}
		Context c = v.getContext();
		int accent = ContextCompat.getColor(c, R.color.accent);
		boolean applied = true;

		if (v instanceof ImageView) {
			Object parent = v.getParent();
			View p = parent instanceof View ? (View) parent : null;
			boolean overflow = v.getClass().getName().endsWith("OverflowMenuButton");
			boolean navButton = v instanceof ImageButton && parent instanceof Toolbar;
			boolean prefIcon = v.getId() == android.R.id.icon && p != null
					&& p.getId() == androidx.preference.R.id.icon_frame;
			if (overflow || navButton || prefIcon) {
				ImageViewCompat.setImageTintList((ImageView) v, ColorStateList.valueOf(tint));
			} else {
				applied = false;
			}
		} else if (v instanceof SwitchCompat) {
			SwitchCompat sw = (SwitchCompat) v;
			sw.setThumbTintList(checked(accent, tint));
			sw.setTrackTintList(checked(withAlpha(accent, 0x80), withAlpha(tint, 0x66)));
		} else if (v instanceof CompoundButton) {
			CompoundButtonCompat.setButtonTintList((CompoundButton) v, checked(accent, tint));
		} else if (v instanceof CheckedTextView) {
			CheckedTextViewCompat.setCheckMarkTintList((CheckedTextView) v, checked(accent, tint));
		} else if (v instanceof EditText) {
			ViewCompat.setBackgroundTintList(v, new ColorStateList(
					new int[][]{{android.R.attr.state_focused}, {}}, new int[]{accent, tint}));
		} else if (v instanceof Spinner) {
			ViewCompat.setBackgroundTintList(v, ColorStateList.valueOf(tint));
		} else if (v instanceof SeekBar) {
			if (Build.VERSION.SDK_INT >= 21) {
				((SeekBar) v).setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(tint, 0x66)));
			}
		} else if (v instanceof TextView
				&& v.getClass().getName().endsWith("ActionMenuItemView")) {
			TextViewCompat.setCompoundDrawableTintList((TextView) v, ColorStateList.valueOf(tint));
		} else {
			applied = false;
		}
		if (applied) {
			APPLIED.put(v, tint);
		}
	}

	private static ColorStateList checked(int checkedColor, int normalColor) {
		return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
				new int[]{checkedColor, normalColor});
	}

	private static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}
}
