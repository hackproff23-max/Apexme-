package ru.playsoftware.j2meloader.theme;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.util.DisplayMetrics;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.OutlinedTextView;

/** Tela de carregamento: logo e "Carregando" com tres pontos que aparecem um por um em 4 segundos. */
public final class LoadingOverlay {
	private static final long TOTAL_MS = 4000;
	private static final int DOTS = 3;

	private LoadingOverlay() {
	}

	public static void show(Activity a) {
		FrameLayout root = new FrameLayout(a);
		root.setBackgroundColor(Color.BLACK);
		root.setClickable(true);
		root.setFocusable(true);

		LinearLayout box = new LinearLayout(a);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);

		DisplayMetrics dm = a.getResources().getDisplayMetrics();
		int size = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.7f);
		ImageView logo = new ImageView(a);
		logo.setImageResource(R.drawable.logo_apexme);
		logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
		box.addView(logo, new LinearLayout.LayoutParams(size, size));

		OutlinedTextView label = new OutlinedTextView(a);
		label.setTextSize(24);
		label.setGravity(Gravity.CENTER);
		box.addView(label, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		root.addView(box, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		((ViewGroup) a.getWindow().getDecorView()).addView(root, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		final String base = a.getString(R.string.apex_loading);
		final Handler h = new Handler(Looper.getMainLooper());
		final long step = TOTAL_MS / DOTS;
		for (int i = 1; i <= DOTS; i++) {
			final int n = i;
			h.postDelayed(() -> setDots(label, base, n), (n - 1) * step);
		}
		h.postDelayed(() -> root.animate().alpha(0f).setDuration(300).withEndAction(() -> {
			ViewGroup parent = (ViewGroup) root.getParent();
			if (parent != null) {
				parent.removeView(root);
			}
		}).start(), TOTAL_MS);
	}

	/** Mostra n pontos; os demais ficam transparentes para o texto nao mudar de largura. */
	private static void setDots(OutlinedTextView tv, String base, int n) {
		StringBuilder sb = new StringBuilder(base);
		for (int i = 0; i < DOTS; i++) {
			sb.append('.');
		}
		SpannableString s = new SpannableString(sb);
		if (n < DOTS) {
			s.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), base.length() + n, sb.length(),
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		tv.setText(s);
	}
}
