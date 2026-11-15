package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerActivity;
import ru.playsoftware.j2meloader.hex.ConverterActivity;
import ru.playsoftware.j2meloader.hex.HexEditorActivity;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;

/**
 * Dock fixa no rodape de todas as telas do app (lista, configuracoes, editores...), menos dentro do jogo:
 * voltar, tela inicial do celular e Editor Java, cada um com logo colorido.
 * Na lista principal tambem tem duas docks no topo (logo + nome do app; teclado, shaders e menu),
 * transparentes: os jogos passam por tras delas.
 * No explorador de arquivos, ao lado da dock ficam dois logos redondos: Cancelar (vermelho) e OK (verde),
 * no lugar dos botoes de texto do seletor.
 */
public final class ApexDock {
	private static final String TAG = "apex_dock";
	private static final String TAG_TOP = "apex_dock_top";
	private static final String TAG_PICKER = "apex_dock_picker";
	/** Margem entre o topo da tela e as docks de cima. */
	private static final int TOP = 8;
	/** App Editor Java (repo Editor-java). */
	private static final String EDITOR_PKG = "org.jareditor.app";
	private static final int BAR_H = 56;
	private static final int SIDE = 16;
	private static final int BOTTOM = 12;
	/** Espaco livre a direita para os botoes flutuantes da lista de jogos. */
	private static final int FAB_SPACE = 88;
	private static final int MAX_W = 320;
	/** No explorador a dock e mais estreita para sobrar lugar para Cancelar/OK ao lado. */
	private static final int PICKER_DOCK_W = 184;
	private static final int PICKER_GAP = 10;
	private static final int PICKER_ACTIONS_W = 124;

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
		bar.addView(cell(a, R.drawable.apexlogo_hex, "Editor Java", () -> editor(a)), cellParams());

		final int baseMargin = ApexUi.dp(a, BOTTOM);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
				barWidth(a, a.getResources().getDisplayMetrics().widthPixels),
				ApexUi.dp(a, BAR_H), Gravity.BOTTOM | Gravity.START);
		lp.setMarginStart(ApexUi.dp(a, SIDE));
		lp.bottomMargin = baseMargin;
		content.addView(bar, lp);

		// com a tela de ponta a ponta, sobe acima da barra de navegacao do sistema
		bindBottomInsets(bar, baseMargin);
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
		if (main) {
			attachTop(a, content);
		}
		if (a instanceof FilteredFilePickerActivity) {
			// o fragmento do seletor pode ainda estar sendo criado: espera o proximo ciclo da tela
			content.post(() -> attachPickerActions(a, content));
		}
	}

	/** Mantem a view acima da barra de navegacao do sistema (quando ela esta visivel). */
	private static void bindBottomInsets(View view, final int baseMargin) {
		ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
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
	}

	/** Explorador de arquivos: Cancelar (vermelho) e OK (verde) como logos redondos ao lado da dock. */
	private static void attachPickerActions(Activity a, FrameLayout content) {
		if (a.isFinishing() || content.findViewWithTag(TAG_PICKER) != null) {
			return;
		}
		final View ok = findLibButton(a, content, "nnf_button_ok", android.R.string.ok);
		final View cancel = findLibButton(a, content, "nnf_button_cancel", android.R.string.cancel);
		hideLibButtons(content, ok, cancel);

		LinearLayout card = new LinearLayout(a);
		card.setTag(TAG_PICKER);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER_VERTICAL);
		card.setBackground(new ApexCardBackground());
		card.setClickable(true);
		int p = ApexUi.dp(a, 6);
		card.setPadding(p, p, p, p);
		card.addView(cell(a, R.drawable.apexlogo_btn_cancel, "Cancelar", () -> {
			if (cancel != null) {
				cancel.performClick();
			} else {
				a.finish();
			}
		}), cellParams());
		card.addView(cell(a, R.drawable.apexlogo_btn_ok, "OK", () -> {
			if (ok != null) {
				ok.performClick();
			}
		}), cellParams());

		int baseMargin = ApexUi.dp(a, BOTTOM);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
				ApexUi.dp(a, PICKER_ACTIONS_W), ApexUi.dp(a, BAR_H), Gravity.BOTTOM | Gravity.START);
		lp.setMarginStart(ApexUi.dp(a, SIDE + PICKER_DOCK_W + PICKER_GAP));
		lp.bottomMargin = baseMargin;
		content.addView(card, lp);
		bindBottomInsets(card, baseMargin);
		ViewCompat.requestApplyInsets(card);
	}

	/** Acha o botao do seletor pelo id da biblioteca; se o id for outro, pelo texto (OK / Cancelar). */
	private static View findLibButton(Activity a, View root, String idName, int textRes) {
		int id = a.getResources().getIdentifier(idName, "id", a.getPackageName());
		if (id != 0) {
			View v = root.findViewById(id);
			if (v != null) {
				return v;
			}
		}
		String want = a.getString(textRes);
		List<View> all = new ArrayList<>();
		collect(root, all);
		for (View v : all) {
			if (v instanceof Button && !(v instanceof CompoundButton)) {
				CharSequence t = ((TextView) v).getText();
				if (t != null && want.equalsIgnoreCase(t.toString().trim())) {
					return v;
				}
			}
		}
		return null;
	}

	private static void collect(View v, List<View> out) {
		out.add(v);
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				collect(g.getChildAt(i), out);
			}
		}
	}

	/**
	 * Esconde os botoes de texto do seletor (e a faixa deles, se ficar vazia) sem tirar da tela
	 * a lista de arquivos. Os cliques passam a ser feitos pelos logos redondos.
	 */
	private static void hideLibButtons(FrameLayout content, View ok, View cancel) {
		if (ok != null) {
			ok.setVisibility(View.GONE);
		}
		if (cancel != null) {
			cancel.setVisibility(View.GONE);
		}
		View cur = ok != null ? ok : cancel;
		for (int level = 0; cur != null && level < 3; level++) {
			ViewParent pp = cur.getParent();
			if (!(pp instanceof ViewGroup) || pp == content) {
				break;
			}
			ViewGroup g = (ViewGroup) pp;
			boolean empty = true;
			for (int i = 0; i < g.getChildCount(); i++) {
				View ch = g.getChildAt(i);
				if (ch == cur || ch.getVisibility() == View.GONE || ch.getClass() == View.class) {
					continue; // escondido ou divisoria simples
				}
				empty = false;
				break;
			}
			if (!empty) {
				break;
			}
			g.setVisibility(View.GONE);
			cur = g;
		}
	}

	/** Docks do topo da lista principal: logo + nome a esquerda; teclado, shaders e menu a direita. */
	private static void attachTop(Activity a, FrameLayout content) {
		if (content.findViewWithTag(TAG_TOP) != null) {
			return;
		}
		// esquerda: icone do app + nome
		LinearLayout brand = new LinearLayout(a);
		brand.setTag(TAG_TOP);
		brand.setOrientation(LinearLayout.HORIZONTAL);
		brand.setGravity(Gravity.CENTER_VERTICAL);
		brand.setBackground(new ApexCardBackground());
		brand.setClickable(true);
		int p = ApexUi.dp(a, 6);
		brand.setPadding(p + ApexUi.dp(a, 2), p, ApexUi.dp(a, 14), p);
		ImageView logo = new ImageView(a);
		logo.setImageResource(R.mipmap.ic_launcher);
		int ls = ApexUi.dp(a, 40);
		brand.addView(logo, new LinearLayout.LayoutParams(ls, ls));
		TextView name = new TextView(a);
		name.setText(R.string.app_name);
		name.setTextSize(22);
		name.setSingleLine(true);
		name.setTextColor(ApexTheme.textColor(a));
		ApexTheme.applyFont(name);
		LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		nl.setMarginStart(ApexUi.dp(a, 10));
		brand.addView(name, nl);
		FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ApexUi.dp(a, BAR_H), Gravity.TOP | Gravity.START);
		bl.setMarginStart(ApexUi.dp(a, SIDE));
		bl.topMargin = ApexUi.dp(a, TOP);
		content.addView(brand, bl);

		// direita: teclado virtual, shaders e menu
		LinearLayout tools = new LinearLayout(a);
		tools.setTag(TAG_TOP);
		tools.setOrientation(LinearLayout.HORIZONTAL);
		tools.setGravity(Gravity.CENTER_VERTICAL);
		tools.setBackground(new ApexCardBackground());
		tools.setClickable(true);
		tools.setPadding(p, p, p, p);
		tools.addView(cell(a, R.drawable.apexlogo_top_keyboard, "Teclado virtual", () -> keyboard(a)), toolParams(a));
		tools.addView(cell(a, R.drawable.apexlogo_top_shaders, "Shaders", () -> shaders(a)), toolParams(a));
		tools.addView(cell(a, R.drawable.apexlogo_top_menu, "Menu", () -> menu(a)), toolParams(a));
		FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ApexUi.dp(a, BAR_H), Gravity.TOP | Gravity.END);
		tl.setMarginEnd(ApexUi.dp(a, SIDE));
		tl.topMargin = ApexUi.dp(a, TOP);
		content.addView(tools, tl);
	}

	private static LinearLayout.LayoutParams toolParams(Activity a) {
		return new LinearLayout.LayoutParams(ApexUi.dp(a, 46), ViewGroup.LayoutParams.MATCH_PARENT);
	}

	private static void keyboard(Activity a) {
		if (a instanceof AppCompatActivity) {
			VkDialog.show((AppCompatActivity) a);
		}
	}

	private static void shaders(Activity a) {
		if (a instanceof AppCompatActivity) {
			ShadersDialog.show((AppCompatActivity) a);
		}
	}

	/** Os mesmos itens do menu de tres pontinhos de antes, em cartoes. */
	private static void menu(Activity a) {
		ApexCardMenu.show(a, "Menu",
				new ApexCardMenu.Item(R.drawable.apexlogo_hex, a.getString(R.string.hex_editor),
						() -> a.startActivity(new Intent(a, HexEditorActivity.class))),
				new ApexCardMenu.Item(R.drawable.apexlogo_converter, a.getString(R.string.hex_converter),
						() -> a.startActivity(new Intent(a, ConverterActivity.class))),
				new ApexCardMenu.Item(R.drawable.apexlogo_settings, a.getString(R.string.action_settings),
						() -> a.startActivity(new Intent(a, SettingsActivity.class))),
				new ApexCardMenu.Item(R.drawable.apexlogo_exit, a.getString(R.string.exit), a::finish));
	}

	/** Dentro do jogo (pacote javax.microedition) nao tem dock. */
	private static boolean excluded(Activity a) {
		return a.getClass().getName().startsWith("javax.microedition.");
	}

	private static int barWidth(Activity a, int contentWidth) {
		if (a instanceof FilteredFilePickerActivity) {
			return ApexUi.dp(a, PICKER_DOCK_W);
		}
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

	/** Abre o app Editor Java direto; sem ele instalado, abre o editor hex do proprio app. */
	private static void editor(Activity a) {
		try {
			Intent i = a.getPackageManager().getLaunchIntentForPackage(EDITOR_PKG);
			if (i != null) {
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				a.startActivity(i);
				return;
			}
		} catch (Exception ignored) {
		}
		if (a instanceof HexEditorActivity) {
			return;
		}
		try {
			a.startActivity(new Intent(a, HexEditorActivity.class));
		} catch (Exception ignored) {
		}
	}
}
