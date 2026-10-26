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
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;

/** Janela do Teclado virtual: edicao, tamanhos, PNG, analogico, letras e mapeamento. */
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

	public static void show(MicroActivity a) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk == null) {
			Toast.makeText(a, "Ative o teclado virtual nas configurações do app", Toast.LENGTH_LONG).show();
			return;
		}
		ScrollView sv = new ScrollView(a);
		LinearLayout root = ApexUi.column(a);
		sv.addView(root);
		AlertDialog[] holder = new AlertDialog[1];

		boolean editing = vk.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF;
		root.addView(ApexUi.button(a, editing ? "Concluir edição" : "Modo edição", v -> {
			holder[0].dismiss();
			if (editing) {
				a.apexVkEditFinish();
			} else {
				a.apexVkEditStart();
			}
		}));

		root.addView(ApexUi.section(a, "Tamanhos"));
		root.addView(ApexUi.slider(a, "Tamanho dos botões", 50, 200,
				Math.round(ApexPrefs.buttonScale() * 100), "%", v -> {
					ApexPrefs.setButtonScale(v / 100f);
					vk.apexRelayout();
				}));
		root.addView(ApexUi.slider(a, "Tamanho do analógico", 50, 200,
				Math.round(ApexPrefs.analogScale() * 100), "%", v -> {
					ApexPrefs.setAnalogScale(v / 100f);
					vk.apexRelayout();
				}));

		root.addView(ApexUi.section(a, "Analógico"));
		root.addView(ApexUi.toggle(a, "Mostrar analógico", ApexPrefs.analogEnabled(), on -> {
			ApexPrefs.setAnalogEnabled(on);
			vk.apexRelayout();
		}));

		root.addView(ApexUi.section(a, "Imagem dos botões (PNG)"));
		root.addView(ApexUi.row(a,
				ApexUi.button(a, "PNG normal", v -> importPng(a, vk, false)),
				ApexUi.button(a, "PNG pressionado", v -> importPng(a, vk, true))));
		root.addView(ApexUi.button(a, "Remover PNGs", v -> {
			ApexPrefs.clearButtonPngs();
			vk.apexRelayout();
		}));

		root.addView(ApexUi.section(a, "Teclado de letras"));
		root.addView(ApexUi.toggle(a, "Mostrar teclado de letras", ApexPrefs.lettersEnabled(), on -> {
			ApexPrefs.setLettersEnabled(on);
			vk.apexRelayout();
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

	private static void importPng(MicroActivity a, VirtualKeyboard vk, boolean pressed) {
		ApexUi.pick(a, "image/*", uri -> {
			boolean ok = ApexPrefs.importButtonPng(a, uri, pressed);
			Toast.makeText(a, ok ? "PNG importado" : "Imagem inválida", Toast.LENGTH_SHORT).show();
			vk.apexRelayout();
		});
	}

	private static void showMapping(MicroActivity a, VirtualKeyboard vk) {
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
					vk.apexRelayout();
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton("Limpar", (d, w) -> {
					ApexPrefs.setNumberMap(new SparseIntArray());
					vk.apexRelayout();
				})
				.show();
	}
}
