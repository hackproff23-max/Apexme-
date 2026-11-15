package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Map;
import java.util.WeakHashMap;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;

/**
 * Dock fixa no rodape de todas as telas do app (lista, configuracoes, editores...), menos dentro do jogo:
 * voltar, tela inicial do celular e tarefas recentes, cada um com logo colorido.
 */
public final class ApexDock {
	private static final String TAG = "apex_dock";
	private static final int BAR_H = 56;
	private static final int SIDE = 16;
	private static final int BOTTOM = 12;
	/** Espaco livre a direita para os botoes flutuantes da lista de jogos. */
	private static final int FAB_SPACE = 88;
	private static final int MAX_W = 320;

	private static final Map<View, Boolean> SHIFTED = new WeakHashMap<>();

	private ApexDock() {
	}

	/** Chamado em cada onResume: poe a dock na tela se ainda nao estiver. */
	public static void attach(Activity a) {
		if (a == null || a.isFinishing() || excluded(a)) {
			return;
		}
		View c = a.findViewById(android.R.id.content);
		if (!(c instanceof FrameLayout)) {
			return;
		}
		final FrameLayout content = (FrameLayout) c;
		if (content.findViewWithTag(TAG) != null) {
			return;
		}
		// na lista principal a grade ja deixa espaco embaixo; nas outras telas o conteudo sobe
		boolean main = a.getClass().getName().equals("ru.playsoftware.j2meloader.MainActivity");
		if (!main) {
			int reserve = ApexUi.dp(a, BAR_H + BOTTOM);
			for (int i = 0; i < content.getChildCount(); i++) {
				View ch = content.getChildAt(i);
				ViewGroup.LayoutParams lp = ch.getLayoutParams();
				if (lp instanceof ViewGroup.MarginLayoutParams && !SHIFTED.containsKey(ch)) {
					((ViewGroup.MarginLayoutParams) lp).bottomMargin += reserve;
					ch.setLayoutParams(lp);
					SHIFTED.put(ch, Boolean.TRUE);
				}
			}
		}

		final LinearLayout bar = new LinearLayout(a);
		bar.setTag(TAG);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setBackground(new ApexCardBackground());
		bar.setClickable(true);
		int p = ApexUi.dp(a, 6);
		bar.setPadding(p, p, p, p);
		bar.addView(cell(a, R.drawable.apexlogo_back, "Voltar", () -> back(a)), cellParams());
		bar.addView(cell(a, R.drawable.apexlogo_home, "Tela inicial", () -> home(a)), cellParams());
		bar.addView(cell(a, R.drawable.apexlogo_recents, "Tarefas recentes", () -> recents(a)), cellParams());

		final int baseMargin = ApexUi.dp(a, BOTTOM);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
				barWidth(a, a.getResources().getDisplayMetrics().widthPixels),
				ApexUi.dp(a, BAR_H), Gravity.BOTTOM | Gravity.START);
		lp.setMarginStart(ApexUi.dp(a, SIDE));
		lp.bottomMargin = baseMargin;
		content.addView(bar, lp);

		// com a tela de ponta a ponta, sobe acima da barra de navegacao do sistema
		ViewCompat.setOnApplyWindowInsetsListener(bar, (v, insets) -> {
			int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
			ViewGroup.LayoutParams l = v.getLayoutParams();
			if (l instanceof FrameLayout.LayoutParams) {
				FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) l;
				int want = baseMargin + nav;
				if (f.bottomMargin != want) {
					f.bottomMargin = want;
					v.setLayoutParams(f);
				}
			}
			return insets;
		});
		// ajusta a largura quando a tela gira
		content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
			ViewGroup.LayoutParams cur = bar.getLayoutParams();
			int w = barWidth(a, r - l);
			if (cur != null && cur.width != w) {
				cur.width = w;
				bar.setLayoutParams(cur);
			}
		});
		ViewCompat.requestApplyInsets(bar);
	}

	/** Dentro do jogo (pacote javax.microedition) nao tem dock. */
	private static boolean excluded(Activity a) {
		return a.getClass().getName().startsWith("javax.microedition.");
	}

	private static int barWidth(Activity a, int contentWidth) {
		int avail = contentWidth - ApexUi.dp(a, SIDE) - ApexUi.dp(a, FAB_SPACE);
		return Math.max(ApexUi.dp(a, 180), Math.min(avail, ApexUi.dp(a, MAX_W)));
	}

	private static LinearLayout.LayoutParams cellParams() {
		return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
	}

	private static View cell(Activity a, int icon, String desc, Runnable click) {
		FrameLayout cell = new FrameLayout(a);
		cell.setClickable(true);
		cell.setContentDescription(desc);
		final ImageView img = new ImageView(a);
		img.setImageResource(icon);
		int s = ApexUi.dp(a, 40);
		cell.addView(img, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
		cell.setOnClickListener(v -> click.run());
		cell.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) {
				img.animate().scaleX(0.84f).scaleY(0.84f).setDuration(80).start();
			} else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
				img.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
			}
			return false;
		});
		return cell;
	}

	@SuppressWarnings("deprecation")
	private static void back(Activity a) {
		if (a instanceof androidx.activity.ComponentActivity) {
			((androidx.activity.ComponentActivity) a).getOnBackPressedDispatcher().onBackPressed();
		} else {
			a.onBackPressed();
		}
	}

	/** Sai para a tela inicial do celular (o app continua nas recentes). */
	private static void home(Activity a) {
		Intent i = new Intent(Intent.ACTION_MAIN);
		i.addCategory(Intent.CATEGORY_HOME);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			a.startActivity(i);
		} catch (Exception ignored) {
		}
	}

	/** Janela de tarefas: o Android so deixa abrir por um servico de acessibilidade. */
	private static void recents(Activity a) {
		if (ApexNavService.recents()) {
			return;
		}
		try {
			new androidx.appcompat.app.AlertDialog.Builder(a)
					.setTitle("Tarefas recentes")
					.setMessage("Para abrir a janela de tarefas, ative o serviço de acessibilidade do app "
							+ "(Acessibilidade > Apps instalados) e volte aqui.")
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton("Abrir ajustes", (d, w) -> openAccessibility(a))
					.show();
		} catch (Throwable t) {
			Toast.makeText(a, "Ative o serviço de acessibilidade do app para usar as tarefas recentes.",
					Toast.LENGTH_LONG).show();
			openAccessibility(a);
		}
	}

	private static void openAccessibility(Activity a) {
		try {
			a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
		} catch (Exception ignored) {
		}
	}
}
