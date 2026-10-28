package ru.playsoftware.j2meloader.apex;

import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.graphics.ShaderProgram;
import android.app.Activity;
import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;

/** Janela de Shaders: importar, selecionar e controle de cores / LCD. */
public final class ShadersDialog {
	private ShadersDialog() {
	}

	private static Canvas canvas(Activity a) {
		if (!(a instanceof MicroActivity)) {
			return null;
		}
		MicroActivity m = (MicroActivity) a;
		return m.getCurrent() instanceof Canvas ? (Canvas) m.getCurrent() : null;
	}

	private static String selLabel() {
		String sel = ApexPrefs.shaderSel();
		if (sel.startsWith(ApexPrefs.SEL_USER)) {
			return sel.substring(ApexPrefs.SEL_USER.length());
		}
		if (ApexPrefs.SEL_BUILTIN.equals(sel)) {
			return "Apex (LCD + cores)";
		}
		return "Automático (do perfil)";
	}

	private static void apply(Activity a, boolean full) {
		Canvas c = canvas(a);
		if (c != null) {
			c.applyApexShader(full);
		}
	}

	public static void show(Activity a) {
		Canvas c = canvas(a);
		ScrollView sv = new ScrollView(a);
		android.widget.LinearLayout root = ApexUi.column(a);
		sv.addView(root);

		if (a instanceof MicroActivity && (c == null || !c.hasGlRenderer())) {
			TextView warn = ApexUi.text(a, "Os efeitos só funcionam com Modo gráfico = OpenGL " +
					"(configurações do app) e com o jogo em execução.", 13);
			root.addView(warn);
		} else if (!(a instanceof MicroActivity)) {
			root.addView(ApexUi.text(a, "O shader escolhido aqui vale para todos os jogos com " +
					"Modo gráfico = OpenGL.", 13));
		}

		TextView current = ApexUi.text(a, "Shader atual: " + selLabel(), 14);
		current.setPadding(0, ApexUi.dp(a, 8), 0, ApexUi.dp(a, 8));

		View importBtn = ApexUi.button(a, "Importar shader", v -> ApexUi.pick(a, "*/*", uri -> {
			String name = ApexPrefs.importShader(a, uri);
			if (name == null) {
				Toast.makeText(a, "Arquivo de shader inválido", Toast.LENGTH_LONG).show();
				return;
			}
			ApexPrefs.setShaderSel(ApexPrefs.SEL_USER + name);
			current.setText("Shader atual: " + selLabel());
			apply(a, true);
			Toast.makeText(a, "Shader importado: " + name, Toast.LENGTH_SHORT).show();
		}));
		View selectBtn = ApexUi.button(a, "Selecionar shader", v -> {
			String[] user = ApexPrefs.listWorkingShaders();
			String[] names = new String[user.length + 2];
			names[0] = "Automático (do perfil)";
			names[1] = "Apex (LCD + cores)";
			System.arraycopy(user, 0, names, 2, user.length);
			String sel = ApexPrefs.shaderSel();
			int checked = 0;
			if (ApexPrefs.SEL_BUILTIN.equals(sel)) {
				checked = 1;
			} else if (sel.startsWith(ApexPrefs.SEL_USER)) {
				String n = sel.substring(ApexPrefs.SEL_USER.length());
				for (int i = 0; i < user.length; i++) {
					if (user[i].equals(n)) {
						checked = i + 2;
					}
				}
			}
			new AlertDialog.Builder(a)
					.setTitle("Selecionar shader")
					.setSingleChoiceItems(names, checked, (d, which) -> {
						if (which == 0) {
							ApexPrefs.setShaderSel(ApexPrefs.SEL_AUTO);
						} else if (which == 1) {
							ApexPrefs.setShaderSel(ApexPrefs.SEL_BUILTIN);
						} else {
							ApexPrefs.setShaderSel(ApexPrefs.SEL_USER + names[which]);
						}
						current.setText("Shader atual: " + selLabel());
						apply(a, true);
						d.dismiss();
					})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
		});
		root.addView(ApexUi.row(a, importBtn, selectBtn));
		root.addView(current);
		root.addView(ApexUi.button(a, "Baixar shaders do libretro", v -> confirmDownload(a, current)));
		root.addView(ApexUi.button(a, "Testar todos os shaders", v -> confirmTest(a, current)));
		root.addView(ApexUi.button(a, "Restaurar shaders com erro", v -> {
			int n = ApexPrefs.badShaders().size();
			ApexPrefs.clearBadShaders();
			Toast.makeText(a, n + " shaders voltaram para a lista", Toast.LENGTH_SHORT).show();
		}));
		root.addView(ApexUi.button(a, "Remover shaders do libretro", v -> {
			int n = LibretroShaders.removeAll();
			current.setText("Shader atual: " + selLabel());
			apply(a, true);
			Toast.makeText(a, n + " shaders removidos", Toast.LENGTH_SHORT).show();
		}));

		root.addView(ApexUi.section(a, "Controle de cores"));
		float[] vals = {ApexPrefs.brightness(), ApexPrefs.contrast(),
				ApexPrefs.saturation(), ApexPrefs.lcdOpacity()};
		root.addView(ApexUi.slider(a, "Saturação", 0, 200, Math.round(vals[2] * 100), "%", v -> {
			vals[2] = v / 100f;
			save(a, vals);
		}));
		root.addView(ApexUi.slider(a, "Brilho", -50, 50, Math.round(vals[0] * 100), "", v -> {
			vals[0] = v / 100f;
			save(a, vals);
		}));
		root.addView(ApexUi.slider(a, "Contraste", 0, 200, Math.round(vals[1] * 100), "%", v -> {
			vals[1] = v / 100f;
			save(a, vals);
		}));
		root.addView(ApexUi.section(a, "Efeito LCD"));
		root.addView(ApexUi.slider(a, "Opacidade do LCD", 0, 100, Math.round(vals[3] * 100), "%", v -> {
			vals[3] = v / 100f;
			save(a, vals);
		}));
		root.addView(ApexUi.text(a, "Cores e LCD valem para os shaders Automático (sem shader no perfil) " +
				"e Apex. Importe shaders GLSL ES 2.0: arquivo único com #if defined(VERTEX) / " +
				"FRAGMENT (estilo RetroArch), ou só o fragment usando sampler0 e v_texcoord0.", 12));

		AlertDialog dialog = new AlertDialog.Builder(a)
				.setTitle(R.string.apex_shaders)
				.setView(sv)
				.setNeutralButton("Restaurar padrão", (d, w) -> {
					ApexPrefs.resetShaderValues();
					apply(a, false);
				})
				.setPositiveButton(R.string.apex_close, null)
				.create();
		dialog.show();
	}

	private static void confirmTest(Activity a, TextView current) {
		Canvas c = canvas(a);
		if (c == null || !c.hasGlRenderer()) {
			Toast.makeText(a, "Abra um jogo com Modo gráfico = OpenGL para testar os shaders",
					Toast.LENGTH_LONG).show();
			return;
		}
		int total = ApexPrefs.listUserShaders().length;
		if (total == 0) {
			Toast.makeText(a, "Nenhum shader importado", Toast.LENGTH_SHORT).show();
			return;
		}
		new AlertDialog.Builder(a)
				.setTitle("Testar todos os shaders")
				.setMessage("Compila os " + total + " shaders neste aparelho, um por um, e esconde da lista " +
						"os que dão erro. A tela do jogo pode piscar durante o teste. Os escondidos podem " +
						"voltar em \"Restaurar shaders com erro\".")
				.setPositiveButton("Testar", (d, w) -> startTest(a, c, current))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void startTest(Activity a, Canvas c, TextView current) {
		ApexPrefs.clearBadShaders();
		String[] names = ApexPrefs.listUserShaders();
		TextView msg = ApexUi.text(a, "Testando…", 14);
		int pad = ApexUi.dp(a, 20);
		msg.setPadding(pad, pad, pad, pad);
		boolean[] cancel = {false};
		AlertDialog progress = new AlertDialog.Builder(a)
				.setTitle("Testando shaders")
				.setView(msg)
				.setCancelable(false)
				.setNegativeButton(android.R.string.cancel, (d, w) -> cancel[0] = true)
				.create();
		progress.show();
		c.testShaders(names, new ShaderProgram.TestListener() {
			@Override
			public void onProgress(int done, int total, int bad) {
				a.runOnUiThread(() -> msg.setText(done + " de " + total + " testados · " + bad + " com erro"));
			}

			@Override
			public void onDone(int ok, int bad, boolean cancelled) {
				a.runOnUiThread(() -> {
					if (a.isFinishing()) {
						return;
					}
					progress.dismiss();
					String sel = ApexPrefs.shaderSel();
					if (sel.startsWith(ApexPrefs.SEL_USER)
							&& ApexPrefs.badShaders().contains(sel.substring(ApexPrefs.SEL_USER.length()))) {
						ApexPrefs.setShaderSel(ApexPrefs.SEL_AUTO);
						apply(a, true);
					}
					current.setText("Shader atual: " + selLabel());
					new AlertDialog.Builder(a).setTitle("Teste dos shaders")
							.setMessage((cancelled ? "Cancelado. " : "") + ok + " funcionam, " + bad +
									" com erro (escondidos da lista).")
							.setPositiveButton(R.string.apex_close, null).show();
				});
			}

			@Override
			public boolean isCancelled() {
				return cancel[0];
			}
		});
	}

	private static void confirmDownload(Activity a, TextView current) {
		new AlertDialog.Builder(a)
				.setTitle("Shaders do libretro")
				.setMessage("Baixa o repositório libretro/glsl-shaders do GitHub (use Wi-Fi) e importa só os " +
						"shaders compatíveis com o emulador: arquivo único que usa apenas a imagem do jogo. " +
						"Os que dependem de vários passes ou de imagens extras são ignorados. Cada shader " +
						"tem a licença do repositório de origem.")
				.setPositiveButton("Baixar", (d, w) -> startDownload(a, current))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void startDownload(Activity a, TextView current) {
		TextView msg = ApexUi.text(a, "Conectando…", 14);
		int pad = ApexUi.dp(a, 20);
		msg.setPadding(pad, pad, pad, pad);
		boolean[] cancel = {false};
		AlertDialog progress = new AlertDialog.Builder(a)
				.setTitle("Baixando shaders")
				.setView(msg)
				.setCancelable(false)
				.setNegativeButton(android.R.string.cancel, (d, w) -> cancel[0] = true)
				.create();
		progress.show();
		new Thread(() -> {
			LibretroShaders.Result r = LibretroShaders.download(new LibretroShaders.Listener() {
				@Override
				public void onProgress(int scanned, int imported) {
					a.runOnUiThread(() -> msg.setText(imported + " importados · " + scanned + " analisados"));
				}

				@Override
				public boolean isCancelled() {
					return cancel[0];
				}
			});
			a.runOnUiThread(() -> {
				if (a.isFinishing()) {
					return;
				}
				progress.dismiss();
				current.setText("Shader atual: " + selLabel());
				String text;
				if (r.error != null) {
					text = "Falha no download: " + r.error + "\nImportados antes da falha: " + r.imported;
				} else if (r.cancelled) {
					text = "Cancelado. Importados: " + r.imported;
				} else {
					text = r.imported + " shaders importados, " + r.skipped + " ignorados (multi-passes ou " +
							"incompatíveis).\nEscolha em \"Selecionar shader\" (nomes começam com libretro__).";
				}
				new AlertDialog.Builder(a).setTitle("Shaders do libretro").setMessage(text)
						.setPositiveButton(R.string.apex_close, null).show();
			});
		}, "ApexLibretroShaders").start();
	}

	private static void save(Activity a, float[] v) {
		ApexPrefs.setColorValues(v[0], v[1], v[2], v[3]);
		apply(a, false);
	}
}
