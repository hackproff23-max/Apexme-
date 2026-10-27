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
			root.addView(ApexUi.button(a, "Ocultar botões", v -> {
				holder[0].dismiss();
				((MicroActivity) a).apexVkHideButtons();
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

		root.addView(ApexUi.section(a, "Botões A e B (Sony)"));
		root.addView(ApexUi.text(a, "Toque em A: modo mover. Toque em B: modo tamanho. Toque no mesmo botão " +
				"de novo para salvar. A e B não enviam tecla ao jogo.", 12));
		root.addView(ApexUi.toggle(a, "Mover e aumentar A e B", ApexPrefs.abEditable(), on -> {
			ApexPrefs.setAbEditable(on);
			relayout(vk);
		}));
		root.addView(ApexUi.slider(a, "Tamanho de A e B", 50, 200,
				Math.round(ApexPrefs.abScale() * 100), "%", v -> {
					ApexPrefs.setAbScale(v / 100f);
					relayout(vk);
				}));

		root.addView(ApexUi.section(a, "D-pad de 8 direções"));
		root.addView(ApexUi.toggle(a, "Mostrar D-pad (as setas continuam)", ApexPrefs.dpadEnabled(), on -> {
			ApexPrefs.setDpadEnabled(on);
			relayout(vk);
		}));
		root.addView(ApexUi.slider(a, "Tamanho do D-pad", 50, 200,
				Math.round(ApexPrefs.dpadScale() * 100), "%", v -> {
					ApexPrefs.setDpadScale(v / 100f);
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

		root.addView(ApexUi.section(a, "Opacidade das imagens"));
		OpacityPreview preview = new OpacityPreview(a);
		root.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ApexUi.dp(a, 96)));
		root.addView(ApexUi.slider(a, "Opacidade do botão normal", 0, 100,
				Math.round(ApexPrefs.normalOpacity() * 100), "%", v -> {
					ApexPrefs.setNormalOpacity(v / 100f);
					preview.invalidate();
					relayout(vk);
				}));
		root.addView(ApexUi.slider(a, "Opacidade do botão pressionado", 0, 100,
				Math.round(ApexPrefs.pressedOpacity() * 100), "%", v -> {
					ApexPrefs.setPressedOpacity(v / 100f);
					preview.invalidate();
					relayout(vk);
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
			items[i] = all[i][1] + (ApexPrefs.hasButtonImage(all[i][0], false) ? "  ✓" : "");
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
				.setItems(new String[]{"Importar imagem", "Remover imagem deste botão"}, (dlg, which) -> {
					if (which == 1) {
						ApexPrefs.clearButtonImage(id);
						relayout(vk);
						showButtonList(a, vk, index);
					} else {
						importImage(a, vk, id, false, index);
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

	/** Exemplo ao vivo: o mesmo botao normal e pressionado com as opacidades escolhidas. */
	private static final class OpacityPreview extends android.view.View {
		private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

		OpacityPreview(Activity a) {
			super(a);
		}

		private android.graphics.Bitmap sample() {
			for (String[] b : ApexPrefs.allButtons()) {
				android.graphics.Bitmap bmp = ApexPrefs.buttonBitmap(b[0], false);
				if (bmp != null) {
					return bmp;
				}
			}
			return null;
		}

		@Override
		protected void onDraw(android.graphics.Canvas c) {
			float d = getResources().getDisplayMetrics().density;
			int w = getWidth();
			int h = getHeight();
			// fundo xadrez para a transparencia ficar visivel
			float cell = 8 * d;
			for (int y = 0; y * cell < h; y++) {
				for (int x = 0; x * cell < w; x++) {
					p.setStyle(android.graphics.Paint.Style.FILL);
					p.setColor(((x + y) & 1) == 0 ? 0xFF555555 : 0xFF777777);
					c.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, p);
				}
			}
			float size = Math.min(h - 28 * d, w / 2f - 24 * d);
			android.graphics.Bitmap bmp = sample();
			for (int i = 0; i < 2; i++) {
				float cx = w * (i == 0 ? 0.27f : 0.73f);
				float top = 6 * d;
				android.graphics.RectF r = new android.graphics.RectF(cx - size / 2, top, cx + size / 2, top + size);
				int alpha = Math.round(255 * (i == 0 ? ApexPrefs.normalOpacity() : ApexPrefs.pressedOpacity()));
				p.setAlpha(alpha);
				if (bmp != null) {
					c.drawBitmap(bmp, null, r, p);
				} else {
					p.setStyle(android.graphics.Paint.Style.FILL);
					p.setColor(0xFF0099FF);
					p.setAlpha(alpha);
					c.drawRoundRect(r, 12 * d, 12 * d, p);
					p.setColor(0xFFFFFFFF);
					p.setAlpha(alpha);
					p.setTextSize(size * 0.4f);
					p.setTextAlign(android.graphics.Paint.Align.CENTER);
					c.drawText("A", cx, r.centerY() + size * 0.14f, p);
				}
				p.setAlpha(255);
				p.setColor(0xFFFFFFFF);
				p.setTextSize(12 * d);
				p.setTextAlign(android.graphics.Paint.Align.CENTER);
				c.drawText(i == 0 ? "Normal" : "Pressionado", cx, h - 6 * d, p);
			}
		}
	}
}
