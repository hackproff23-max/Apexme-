package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.SharedPreferences;

import javax.microedition.util.ContextHolder;

/** Botao que liga/desliga o teclado de letras: aparece na lista "Ocultar botoes" e pode ser escondido. */
public final class ApexLetterToggle {
	private static final String FILE = "apex_letter_toggle";
	private static final String K_HIDDEN = "hidden";

	private static SharedPreferences sp;

	private ApexLetterToggle() {
	}

	private static synchronized SharedPreferences sp() {
		if (sp == null) {
			sp = ContextHolder.getAppContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
		}
		return sp;
	}

	/** True se o botao estiver oculto (padrao: visivel). */
	public static boolean hidden() {
		return sp().getBoolean(K_HIDDEN, false);
	}

	public static void setHidden(boolean v) {
		sp().edit().putBoolean(K_HIDDEN, v).apply();
	}
}
