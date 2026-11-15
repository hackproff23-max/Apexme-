package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

/**
 * AlertDialog.Builder que abre a janela em formato de janela: largura limitada e centralizada,
 * altura so do tamanho do conteudo e no maximo 78% da tela (o resto rola). Assim as janelas
 * longas (Shaders, Teclado virtual...) nao ocupam a tela inteira.
 */
public final class ApexDialogBuilder extends AlertDialog.Builder {
	private static final int MAX_WIDTH_DP = 400;
	private static final int SIDE_MARGIN_DP = 24;
	private static final float MAX_HEIGHT = 0.78f;

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

	private static void fit(AlertDialog d, Context c) {
		final Window w = d.getWindow();
		if (w == null) {
			return;
		}
		DisplayMetrics dm = c.getResources().getDisplayMetrics();
		final int width = Math.min((int) (MAX_WIDTH_DP * dm.density),
				dm.widthPixels - (int) (SIDE_MARGIN_DP * dm.density));
		final int maxH = (int) (dm.heightPixels * MAX_HEIGHT);
		w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
		w.setGravity(Gravity.CENTER);
		final View decor = w.getDecorView();
		decor.post(() -> {
			if (decor.getHeight() > maxH) {
				w.setLayout(width, maxH);
			}
		});
	}
}
