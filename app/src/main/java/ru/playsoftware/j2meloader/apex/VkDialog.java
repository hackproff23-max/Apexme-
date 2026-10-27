package ru.playsoftware.j2meloader.apex;

import android.util.SparseIntArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import android.app.Activity;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;

/** Janela do Teclado virtual: edicao, tamanhos, imagem por botao, analogico, letras e mapeamento. */
public final class VkDialog {
	private static final String[] NUM_LABELS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "*", "#"};
	private static final int[] NUM_CODES = {49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 42, 35};
	private static final String[] LETTERS = new String[27];

	static {
		LETTERS[0] = "—";
		for (int i = 0; i < 26; i++) {
			LETTERS[i + 1] = String.valueOf((char) ('A' + i));
		}
	}

	private VkDialog() {
	}

	/** Aplica na hora se houver jogo rodando (vk != null); fora do jogo so grava as opcoes. */
	private static void relayout(VirtualKeyboard vk) {
		if (vk != null) {
			vk.apexRelayout();
		}
	}

	public static void show(Activity a) {
		VirtualKeyboard vk = a instanceof MicroActivity ? ContextHolder.getVk() : null;
		if (a instanceof MicroActivity && vk == null) {
			Toast.makeText(a, "Ative o teclado virtual nas configurações do app", Toast.LENGTH_LONG).show();
			return;
		}
		ScrollView sv = new ScrollView(a);
		LinearLayout root = ApexUi.column(a);
		sv.addView(root);
		AlertDialog[] holder = new AlertDialog[1];

		if (vk != null) {
			boolean editing = vk.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF;
			root.addView(ApexUi.button(a, editing ? "Concluir edição" : "Modo edição", v -> {
				holder[0].dismiss();
				if (editing) {
					((MicroActivity) a).apexVkEditFinish();
				} else {
					((MicroActivity) a).apexVkEditStart();
				}
			}));
		} else {
			root.addView(ApexUi.text(a, "Estas opções valem para todos os jogos. Para arrastar o analógico e " +
					"as letras na tela, abra um jogo e use o Modo edição.", 12));
		}

		root.addView(ApexUi.section(a, "Tamanhos"));
		root.addView(ApexUi.slider(a, "Tamanho dos botões", 50, 200,
				Math.round(ApexPrefs.buttonScale() * 100), "%", v -> {
					ApexPrefs.setButtonScale(v / 100f);
					relayout(vk);
				}));
		root.addView(ApexUi.slider(a, "Tamanho do analógico", 50, 200,
				Math.round(ApexPrefs.analogScale() * 100), "%", v -> {
					ApexPrefs.setAnalogScale(v / 100f);
					relayout(vk);
				}));

		root.addView(ApexUi.section(a, "Analógico"));
		root.addView(ApexUi.toggle(a, "Mostrar analógico", ApexPrefs.analogEnabled(), on -> {
			ApexPrefs.setAnalogEnabled(on);
			relayout(vk);
		}));

		root.addView(ApexUi.section(a, "Imagem de cada botão"));
		root.addView(ApexUi.button(a, "Escolher imagem por botão", v -> {
			holder[0].dismiss();
			showButtonList(a, vk, 0);
		}));
		root.addView(ApexUi.button(a, "Remover todas as imagens", v -> {
			ApexPrefs.clearAllButtonImages();
			relayout(vk);
			Toast.makeText(a, "Imagens removidas", Toast.LENGTH_SHORT).show();
		}));

		root.addView(ApexUi.section(a, "Teclado de letras"));
		root.addView(ApexUi.toggle(a, "Mostrar teclado de letras", ApexPrefs.lettersEnabled(), on -> {
			ApexPrefs.setLettersEnabled(on);
			relayout(vk);
		}));
		root.addView(ApexUi.button(a, "Mapear números nas letras", v -> {
			holder[0].dismiss();
			showMapping(a, vk);
		}));
		root.addView(ApexUi.text(a, "Dica: use o Modo edição para arrastar o analógico e o teclado de letras.", 12));

		holder[0] = new AlertDialog.Builder(a)
				.setTitle(R.string.PREF_VIRTUAL_KEYBOARD_OPTIONS)
				.setView(sv)
				.setPositiveButton(R.string.apex_close, null)
				.create();
		holder[0].show();
	}

	/** Lista todos os botoes; toque em um para definir a imagem so dele. */
	private static void showButtonList(Activity a, VirtualKeyboard vk, int scrollTo) {
		String[][] all = ApexPrefs.allButtons();
		String[] items = new String[all.length];
		for (int i = 0; i < all.length; i++) {
			boolean n = ApexPrefs.hasButtonImage(all[i][0], false);
			boolean p = ApexPrefs.hasButtonImage(all[i][0], true);
			items[i] = all[i][1] + (n || p ? "  ✓" + (n ? " normal" : "") + (p ? " pressionado" : "") : "");
		}
		AlertDialog d = new AlertDialog.Builder(a)
				.setTitle("Imagem de qual botão?")
				.setItems(items, (dlg, which) -> showButtonOptions(a, vk, all[which], which))
				.setNegativeButton(R.string.apex_close, null)
				.create();
		d.show();
		d.getListView().setSelection(scrollTo);
	}

	private static void showButtonOptions(Activity a, VirtualKeyboard vk, String[] btn, int index) {
		String id = btn[0];
		new AlertDialog.Builder(a)
				.setTitle(btn[1])
				.setItems(new String[]{"Importar imagem normal", "Importar imagem pressionada",
						"Remover imagens deste botão"}, (dlg, which) -> {
					if (which == 2) {
						ApexPrefs.clearButtonImage(id);
						relayout(vk);
						showButtonList(a, vk, index);
					} else {
						importImage(a, vk, id, which == 1, index);
					}
				})
				.setNegativeButton(android.R.string.cancel, (dlg, w) -> showButtonList(a, vk, index))
				.setOnCancelListener(dlg -> showButtonList(a, vk, index))
				.show();
	}

	private static void importImage(Activity a, VirtualKeyboard vk, String id, boolean pressed, int index) {
		ApexUi.pick(a, "image/*", uri -> {
			boolean ok = ApexPrefs.importButtonImage(a, uri, id, pressed);
			Toast.makeText(a, ok ? "Imagem importada" : "Imagem inválida", Toast.LENGTH_SHORT).show();
			relayout(vk);
			showButtonList(a, vk, index);
		});
	}

	private static void showMapping(Activity a, VirtualKeyboard vk) {
		ScrollView sv = new ScrollView(a);
		LinearLayout root = ApexUi.column(a);
		sv.addView(root);
		root.addView(ApexUi.text(a, "Escolha a letra de cada número. O botão passa a mostrar a letra " +
				"e envia a letra para o jogo (— = continua enviando o número). " +
				"Vale também para teclado físico/Bluetooth.", 12));
		Spinner[] spinners = new Spinner[NUM_CODES.length];
		for (int i = 0; i < NUM_CODES.length; i++) {
			LinearLayout row = new LinearLayout(a);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setPadding(0, ApexUi.dp(a, 4), 0, ApexUi.dp(a, 4));
			TextView t = ApexUi.text(a, NUM_LABELS[i], 18);
			row.addView(t, new LinearLayout.LayoutParams(ApexUi.dp(a, 48), LinearLayout.LayoutParams.WRAP_CONTENT));
			Spinner sp = new Spinner(a);
			ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, LETTERS);
			ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			sp.setAdapter(ad);
			int cur = ApexPrefs.numberTarget(NUM_CODES[i]);
			if (cur >= 'a' && cur <= 'z') {
				sp.setSelection(cur - 'a' + 1);
			}
			spinners[i] = sp;
			row.addView(sp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			root.addView(row);
		}
		new AlertDialog.Builder(a)
				.setTitle("Mapear números nas letras")
				.setView(sv)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					SparseIntArray map = new SparseIntArray();
					for (int i = 0; i < NUM_CODES.length; i++) {
						int pos = spinners[i].getSelectedItemPosition();
						if (pos > 0) {
							map.put(NUM_CODES[i], 'a' + pos - 1);
						}
					}
					ApexPrefs.setNumberMap(map);
					relayout(vk);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton("Limpar", (d, w) -> {
					ApexPrefs.setNumberMap(new SparseIntArray());
					relayout(vk);
				})
				.show();
	}
}
