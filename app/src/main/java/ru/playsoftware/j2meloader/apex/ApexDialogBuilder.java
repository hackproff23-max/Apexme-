package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import javax.microedition.shell.MicroActivity;

/**
 * AlertDialog.Builder que abre a janela em formato de janela compacta, sem ocupar a tela inteira:
 * - dentro do jogo: painel estreito colado na esquerda (o jogo continua visivel ao lado, sem escurecer);
 * - fora do jogo: janela pequena no canto superior direito, como o menu da tela inicial.
 * A altura e a do conteudo e passa a rolar quando chega no limite.
 */
public final class ApexDialogBuilder extends AlertDialog.Builder {
	private static final int GAME_MAX_WIDTH_DP = 240;
	private static final float GAME_MAX_WIDTH_FRAC = 0.34f;
	private static final float GAME_MAX_HEIGHT = 0.96f;
	private static final int MENU_MAX_WIDTH_DP = 224;
	private static final float MENU_MAX_HEIGHT = 0.70f;
	private static final int MARGIN_DP = 8;
	/** Tamanhos (sp) do titulo, da mensagem e dos botoes da janela. */
	private static final float TITLE_SP = 15f;
	private static final float MESSAGE_SP = 12f;
	private static final float BUTTON_SP = 12f;

	public ApexDialogBuilder(@NonNull Context context) {
		super(context);
	}

	@NonNull
	@Override
	public AlertDialog create() {
		AlertDialog d = super.create();
		final Context c = getContext();
		d.setOnShowListener(x -> {
			fit(d, c);
			shrinkChrome(d);
		});
		return d;
	}

	private static boolean inGame(Context c) {
		while (c instanceof ContextWrapper) {
			if (c instanceof MicroActivity) {
				return true;
			}
			c = ((ContextWrapper) c).getBaseContext();
		}
		return false;
	}

	/** Titulo, mensagem e botoes (FECHAR, RESTAURAR...) tambem em tamanho compacto. */
	private static void shrinkChrome(AlertDialog d) {
		TextView title = d.findViewById(androidx.appcompat.R.id.alertTitle);
		if (title != null) {
			title.setTextSize(TITLE_SP);
		}
		TextView message = d.findViewById(android.R.id.message);
		if (message != null) {
			message.setTextSize(MESSAGE_SP);
		}
		int[] which = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL};
		for (int w : which) {
			Button b = d.getButton(w);
			if (b != null) {
				b.setTextSize(BUTTON_SP);
				b.setMinHeight(0);
				b.setMinimumHeight(0);
				b.setMinWidth(0);
				b.setMinimumWidth(0);
			}
		}
	}

	private static void fit(AlertDialog d, Context c) {
		final Window w = d.getWindow();
		if (w == null) {
			return;
		}
		DisplayMetrics dm = c.getResources().getDisplayMetrics();
		float den = dm.density;
		int margin = (int) (MARGIN_DP * den);
		boolean game = inGame(c);
		boolean landscape = dm.widthPixels > dm.heightPixels;

		final int width;
		final int maxH;
		final WindowManager.LayoutParams lp = w.getAttributes();
		if (game) {
			int byFrac = landscape ? (int) (dm.widthPixels * GAME_MAX_WIDTH_FRAC) : Integer.MAX_VALUE;
			width = Math.min(Math.min((int) (GAME_MAX_WIDTH_DP * den), byFrac), dm.widthPixels - 2 * margin);
			maxH = (int) (dm.heightPixels * GAME_MAX_HEIGHT);
			lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
			lp.x = margin;
			lp.y = 0;
			lp.dimAmount = 0f;
		} else {
			width = Math.min((int) (MENU_MAX_WIDTH_DP * den), dm.widthPixels - 2 * margin);
			maxH = (int) (dm.heightPixels * MENU_MAX_HEIGHT);
			lp.gravity = Gravity.TOP | Gravity.END;
			lp.x = margin;
			lp.y = margin;
			lp.dimAmount = 0.25f;
		}
		lp.width = width;
		lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
		w.setAttributes(lp);

		final View decor = w.getDecorView();
		decor.post(() -> {
			if (decor.getHeight() > maxH) {
				WindowManager.LayoutParams l2 = w.getAttributes();
				l2.height = maxH;
				w.setAttributes(l2);
			}
		});
	}
}
