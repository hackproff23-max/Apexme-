package ru.playsoftware.j2meloader.apex;

import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import javax.microedition.lcdui.Canvas;
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
			String[] user = ApexPrefs.listUserShaders();
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

	private static void save(Activity a, float[] v) {
		ApexPrefs.setColorValues(v[0], v[1], v[2], v[3]);
		apply(a, false);
	}
}
