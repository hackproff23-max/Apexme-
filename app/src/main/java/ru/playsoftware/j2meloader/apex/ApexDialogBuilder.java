package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import javax.microedition.shell.MicroActivity;

/**
 * AlertDialog.Builder que abre a janela em formato de janela, sem ocupar a tela inteira:
 * - dentro do jogo: painel colado na esquerda (o jogo continua visivel ao lado, sem escurecer);
 * - fora do jogo: janela compacta no canto superior direito, como o menu da tela inicial.
 * A altura e a do conteudo e passa a rolar quando chega no limite.
 */
public final class ApexDialogBuilder extends AlertDialog.Builder {
	private static final int GAME_MAX_WIDTH_DP = 320;
	private static final float GAME_MAX_WIDTH_FRAC = 0.40f;
	private static final float GAME_MAX_HEIGHT = 0.96f;
	private static final int MENU_MAX_WIDTH_DP = 284;
	private static final float MENU_MAX_HEIGHT = 0.78f;
	private static final int MARGIN_DP = 8;

	public ApexDialogBuilder(@NonNull Context context) {
		super(context);
	}

	@NonNull
	@Override
	public AlertDialog create() {
		AlertDialog d = super.create();
		final Context c = getContext();
		d.setOnShowListener(x -> fit(d, c));
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
