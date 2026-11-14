package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.lang.ref.WeakReference;

import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.BuildConfig;

/**
 * Tema do controle (teclado virtual): escuro (padrao) ou claro.
 * Nas configuracoes vale como padrao para todos os jogos; dentro do jogo vale so para aquele jogo.
 */
public final class ApexControlTheme {
	public static final int FOLLOW = -1;
	public static final int DARK = 0;
	public static final int LIGHT = 1;

	private static final String FILE = "apex_control_theme";
	private static final String K_DEFAULT = "default";
	private static final String K_GAME = "game:";
	private static final String[] NAMES = {"Escuro", "Claro"};

	private static SharedPreferences sp;
	private static WeakReference<Activity> cachedAct;
	private static volatile int cachedTheme = -1;

	private ApexControlTheme() {
	}

	private static synchronized SharedPreferences sp() {
		if (sp == null) {
			sp = ContextHolder.getAppContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
		}
		return sp;
	}

	/** Tema padrao (das configuracoes): DARK ou LIGHT. */
	public static int defaultTheme() {
		return sp().getInt(K_DEFAULT, DARK) == LIGHT ? LIGHT : DARK;
	}

	public static String defaultName() {
		return NAMES[defaultTheme()];
	}

	private static void setDefault(int v) {
		sp().edit().putInt(K_DEFAULT, v == LIGHT ? LIGHT : DARK).apply();
		cachedTheme = -1;
	}

	private static void setGame(String id, int v) {
		SharedPreferences.Editor e = sp().edit();
		if (v == DARK || v == LIGHT) {
			e.putInt(K_GAME + id, v);
		} else {
			e.remove(K_GAME + id);
		}
		e.apply();
		cachedTheme = -1;
	}

	/** Nome da pasta do jogo aberto (a mesma usada no perfil), ou null fora do jogo. */
	private static String gameId(Activity a) {
		if (!(a instanceof MicroActivity)) {
			return null;
		}
		try {
			String path;
			if (BuildConfig.FULL_EMULATOR) {
				Uri data = a.getIntent().getData();
				if (data == null) {
					return null;
				}
				path = data.toString();
			} else {
				path = a.getApplicationInfo().dataDir + "/files/converted/midlet";
			}
			String name = new File(path).getName();
			return name.isEmpty() ? null : name;
		} catch (Throwable e) {
			return null;
		}
	}

	private static int resolve(Activity a) {
		int def = defaultTheme();
		String id = gameId(a);
		if (id == null) {
			return def;
		}
		int g = sp().getInt(K_GAME + id, FOLLOW);
		return g == DARK || g == LIGHT ? g : def;
	}

	/** True se o controle deve ser desenhado no tema claro (chamado a cada desenho; usa cache). */
	public static boolean light() {
		Activity a = ContextHolder.getActivity();
		WeakReference<Activity> ref = cachedAct;
		int t = cachedTheme;
		if (a != null && ref != null && ref.get() == a && t >= 0) {
			return t == LIGHT;
		}
		t = resolve(a);
		if (a != null) {
			cachedAct = new WeakReference<>(a);
			cachedTheme = t;
		}
		return t == LIGHT;
	}

	/** Janela dentro do jogo: o tema escolhido vale so para este jogo. */
	public static void showGame(MicroActivity a) {
		if (a.isFinishing()) {
			return;
		}
		final String id = gameId(a);
		if (id == null) {
			Toast.makeText(a, "Não foi possível identificar o jogo", Toast.LENGTH_SHORT).show();
			return;
		}
		int cur = sp().getInt(K_GAME + id, FOLLOW);
		String[] items = {"Seguir o padrão (" + defaultName() + ")", NAMES[0], NAMES[1]};
		int sel = cur == DARK || cur == LIGHT ? cur + 1 : 0;
		new AlertDialog.Builder(a)
				.setTitle("Tema do controle (só este jogo)")
				.setSingleChoiceItems(items, sel, (dlg, which) -> {
					setGame(id, which == 0 ? FOLLOW : which - 1);
					dlg.dismiss();
					VirtualKeyboard vk = ContextHolder.getVk();
					if (vk != null) {
						vk.apexRelayout();
					} else {
						Toast.makeText(a, "Ative o teclado virtual para ver o controle", Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	/** Janela das configuracoes: o tema escolhido e o padrao de todos os jogos. */
	public static void showDefault(Context ctx, Runnable after) {
		new AlertDialog.Builder(ctx)
				.setTitle("Tema padrão do controle")
				.setSingleChoiceItems(NAMES, defaultTheme(), (dlg, which) -> {
					setDefault(which);
					dlg.dismiss();
					if (after != null) {
						after.run();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}
