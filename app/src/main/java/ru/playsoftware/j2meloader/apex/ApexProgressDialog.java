package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.theme.ApexWindowBackground;

/**
 * Janela de progresso (conversao de jogos) com a ApexProgressBar.
 * Tem a mesma "cara" de um AlertDialog (setTitle/setMessage/show/dismiss), e le o "n/total"
 * da primeira linha da mensagem para mover a barra.
 */
public final class ApexProgressDialog {
	private static final Pattern STEP = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

	private final Activity activity;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Dialog dialog;
	private final TextView title;
	private final TextView status;
	private final TextView name;
	private final ApexProgressBar bar;
	private boolean finishing;

	private ApexProgressDialog(Activity a) {
		activity = a;
		dialog = new Dialog(a);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		int pad = ApexUi.dp(a, 20);
		LinearLayout root = new LinearLayout(a);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);

		title = ApexUi.text(a, "", 22);
		root.addView(title);

		bar = new ApexProgressBar(a);
		LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		blp.topMargin = ApexUi.dp(a, 10);
		root.addView(bar, blp);

		status = ApexUi.text(a, "", 16);
		root.addView(status);

		name = ApexUi.text(a, "", 14);
		name.setAlpha(0.8f);
		name.setSingleLine(true);
		name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
		root.addView(name);

		FrameLayout frame = new FrameLayout(a);
		frame.setBackground(new ApexWindowBackground());
		frame.addView(root, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
		dialog.setContentView(frame);
		dialog.setCancelable(false);

		Window w = dialog.getWindow();
		if (w != null) {
			w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			w.setGravity(Gravity.CENTER);
			WindowManager.LayoutParams lp = w.getAttributes();
			int screenW = a.getResources().getDisplayMetrics().widthPixels;
			lp.width = Math.min(ApexUi.dp(a, 380), screenW - ApexUi.dp(a, 24));
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			w.setAttributes(lp);
		}
		bar.setProgress(0f, 0.04f);
	}

	public static ApexProgressDialog builder(Activity a) {
		return new ApexProgressDialog(a);
	}

	public ApexProgressDialog setTitle(int resId) {
		title.setText(activity.getString(resId));
		return this;
	}

	public ApexProgressDialog setTitle(CharSequence t) {
		title.setText(t);
		return this;
	}

	public ApexProgressDialog setCancelable(boolean cancelable) {
		dialog.setCancelable(cancelable);
		return this;
	}

	public ApexProgressDialog create() {
		return this;
	}

	/** Primeira linha: "Convertendo n/total"; resto: nome do arquivo. */
	public ApexProgressDialog setMessage(CharSequence message) {
		String m = message == null ? "" : message.toString();
		int nl = m.indexOf('\n');
		String first = nl >= 0 ? m.substring(0, nl) : m;
		String rest = nl >= 0 ? m.substring(nl + 1) : "";
		status.setText(first);
		name.setText(rest);
		Matcher mt = STEP.matcher(first);
		if (mt.find()) {
			try {
				int idx = Integer.parseInt(mt.group(1));
				int total = Math.max(1, Integer.parseInt(mt.group(2)));
				float base = Math.max(0, idx - 1) / (float) total;
				float slice = 1f / total;
				bar.setProgress(base, base + slice * 0.92f);
			} catch (NumberFormatException ignored) {
			}
		}
		return this;
	}

	public void show() {
		if (!activity.isFinishing()) {
			dialog.show();
		}
	}

	/** Leva a barra a 100% (com clarao) e so entao fecha a janela. */
	public void dismiss() {
		if (finishing) {
			return;
		}
		finishing = true;
		bar.complete();
		handler.postDelayed(() -> {
			try {
				if (dialog.isShowing()) {
					dialog.dismiss();
				}
			} catch (Exception ignored) {
			}
		}, 1100);
	}
}
