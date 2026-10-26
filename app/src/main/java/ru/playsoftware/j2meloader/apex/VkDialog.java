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
	private static final String[] TARGETS = {"—", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "#"};
	private static final int[] TARGET_CODES = {-1, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 42, 35};

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
		root.addView(ApexUi.button(a, "Mapear letras nos números", v -> {
			holder[0].dismiss();
			showMapping(a);
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

	private static void showMapping(MicroActivity a) {
		ScrollView sv = new ScrollView(a);
		LinearLayout root = ApexUi.column(a);
		sv.addView(root);
		root.addView(ApexUi.text(a, "Escolha qual número cada letra vai enviar (— = envia a própria letra). " +
				"Vale para o teclado de letras e para teclado físico/Bluetooth.", 12));
		Spinner[] spinners = new Spinner[26];
		for (int i = 0; i < 26; i++) {
			char letter = (char) ('a' + i);
			LinearLayout row = new LinearLayout(a);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setPadding(0, ApexUi.dp(a, 4), 0, ApexUi.dp(a, 4));
			TextView t = ApexUi.text(a, String.valueOf(Character.toUpperCase(letter)), 18);
			row.addView(t, new LinearLayout.LayoutParams(ApexUi.dp(a, 48), LinearLayout.LayoutParams.WRAP_CONTENT));
			Spinner sp = new Spinner(a);
			ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, TARGETS);
			ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			sp.setAdapter(ad);
			int cur = ApexPrefs.letterTarget(letter);
			for (int j = 0; j < TARGET_CODES.length; j++) {
				if (TARGET_CODES[j] == cur) {
					sp.setSelection(j);
				}
			}
			spinners[i] = sp;
			row.addView(sp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			root.addView(row);
		}
		new AlertDialog.Builder(a)
				.setTitle("Mapear letras nos números")
				.setView(sv)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					SparseIntArray map = new SparseIntArray();
					for (int i = 0; i < 26; i++) {
						int code = TARGET_CODES[spinners[i].getSelectedItemPosition()];
						if (code != -1) {
							map.put('a' + i, code);
						}
					}
					ApexPrefs.setLetterMap(map);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton("Limpar", (d, w) -> ApexPrefs.setLetterMap(new SparseIntArray()))
				.show();
	}
}
