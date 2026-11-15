package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.ApexGridView;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.theme.ApexWindowBackground;

/**
 * Menu em cartoes no estilo da tela de Configuracoes: titulo e uma linha por opcao
 * (logo colorido, nome e seta ou marca de selecionado).
 * Quando vem do toque longo num jogo da grade, abre compacto e colado no cartao tocado.
 */
public final class ApexCardMenu {
	private ApexCardMenu() {
	}

	public static final class Item {
		final int icon;
		final String label;
		final boolean checked;
		final Runnable action;

		public Item(int icon, String label, Runnable action) {
			this(icon, label, false, action);
		}

		public Item(int icon, String label, boolean checked, Runnable action) {
			this.icon = icon;
			this.label = label;
			this.checked = checked;
			this.action = action;
		}
	}

	public static void show(Activity a, String title, Item... items) {
		show(a, title, ApexGridView.takeAnchor(), items);
	}

	/** anchor != null: menu compacto ao lado desse retangulo (coordenadas da tela). */
	public static void show(Activity a, String title, Rect anchor, Item... items) {
		if (a.isFinishing()) {
			return;
		}
		final boolean compact = anchor != null;
		Dialog d = new Dialog(a);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);

		LinearLayout list = new LinearLayout(a);
		list.setOrientation(LinearLayout.VERTICAL);
		int pad = ApexUi.dp(a, compact ? 8 : 16);
		list.setPadding(pad, pad, pad, pad);

		TextView t = new TextView(a);
		t.setText(title);
		t.setTextSize(compact ? 14 : 24);
		t.setTextColor(ApexTheme.textColor(a));
		t.setPadding(ApexUi.dp(a, 4), 0, 0, ApexUi.dp(a, compact ? 4 : 6));
		if (compact) {
			t.setMaxLines(1);
			t.setEllipsize(TextUtils.TruncateAt.END);
		}
		ApexTheme.applyFont(t);
		list.addView(t);

		for (Item it : items) {
			list.addView(row(a, it, compact, () -> {
				d.dismiss();
				if (it.action != null) {
					it.action.run();
				}
			}));
		}

		ScrollView scroll = new ScrollView(a);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.addView(list);
		FrameLayout frame = new FrameLayout(a);
		frame.setBackground(new ApexWindowBackground());
		frame.addView(scroll, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
		d.setContentView(frame);

		Window w = d.getWindow();
		if (w != null) {
			w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			WindowManager.LayoutParams lp = w.getAttributes();
			DisplayMetrics dm = a.getResources().getDisplayMetrics();
			if (compact) {
				int width = Math.max(ApexUi.dp(a, 156), Math.min(ApexUi.dp(a, 184), (int) (dm.widthPixels * 0.46f)));
				lp.width = width;
				lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
				frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
						View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
				int h = frame.getMeasuredHeight();
				Rect vis = new Rect();
				a.getWindow().getDecorView().getWindowVisibleDisplayFrame(vis);
				int gap = ApexUi.dp(a, 4);
				// ao lado do jogo tocado: a direita se couber, senao a esquerda
				int x = anchor.right + gap;
				if (x + width > dm.widthPixels - gap) {
					x = anchor.left - gap - width;
				}
				if (x < gap) {
					x = Math.max(gap, (dm.widthPixels - width) / 2);
				}
				int y = Math.max(vis.top + gap, Math.min(anchor.top, vis.bottom - h - gap));
				w.setGravity(Gravity.TOP | Gravity.START);
				lp.x = x - vis.left;
				lp.y = y - vis.top;
				lp.dimAmount = 0.25f;
			} else {
				w.setGravity(Gravity.CENTER);
				lp.width = Math.min(ApexUi.dp(a, 340), dm.widthPixels - ApexUi.dp(a, 24));
				lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			}
			w.setAttributes(lp);
		}
		d.show();
	}

	private static View row(Activity a, Item it, boolean compact, Runnable click) {
		LinearLayout row = new LinearLayout(a);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(new ApexCardBackground());
		if (compact) {
			row.setPadding(ApexUi.dp(a, 5), ApexUi.dp(a, 5), ApexUi.dp(a, 8), ApexUi.dp(a, 5));
		} else {
			row.setPadding(ApexUi.dp(a, 10), ApexUi.dp(a, 10), ApexUi.dp(a, 14), ApexUi.dp(a, 10));
		}
		row.setClickable(true);

		ImageView logo = new ImageView(a);
		logo.setImageResource(it.icon);
		int s = ApexUi.dp(a, compact ? 28 : 44);
		row.addView(logo, new LinearLayout.LayoutParams(s, s));

		TextView label = new TextView(a);
		label.setText(it.label);
		label.setTextSize(compact ? 13 : 20);
		label.setTextColor(ApexTheme.textColor(a));
		if (compact) {
			label.setMaxLines(2);
		}
		ApexTheme.applyFont(label);
		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		tlp.leftMargin = ApexUi.dp(a, compact ? 8 : 14);
		row.addView(label, tlp);

		if (it.checked) {
			TextView mark = new TextView(a);
			mark.setText("✓");
			mark.setTextSize(compact ? 14 : 22);
			mark.setTextColor(ApexTheme.pressedColor(a));
			row.addView(mark);
		} else {
			ImageView chevron = new ImageView(a);
			chevron.setImageResource(R.drawable.ic_apex_chevron);
			chevron.setColorFilter(ApexTheme.textColor(a));
			chevron.setAlpha(0.6f);
			int cs = ApexUi.dp(a, compact ? 14 : 22);
			row.addView(chevron, new LinearLayout.LayoutParams(cs, cs));
		}

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.topMargin = ApexUi.dp(a, compact ? 4 : 8);
		row.setLayoutParams(lp);
		row.setOnClickListener(v -> click.run());
		return row;
	}
}
