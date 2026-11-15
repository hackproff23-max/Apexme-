package ru.playsoftware.j2meloader.apex;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.woesss.j2me.installer.DeepDuplicateScanner;
import ru.woesss.j2me.installer.DeepDuplicateScanner.Extra;
import ru.woesss.j2me.installer.DeepDuplicateScanner.ExtraKind;
import ru.woesss.j2me.installer.DeepDuplicateScanner.FileGroup;
import ru.woesss.j2me.installer.DeepDuplicateScanner.Game;
import ru.woesss.j2me.installer.DeepDuplicateScanner.Group;
import ru.woesss.j2me.installer.DuplicateCleaner;

/**
 * Botao "Duplicatas": analise profunda de todo o app (jogos, pastas sem dono e arquivos repetidos).
 * Mostra tudo que achou com caixas para marcar; so apaga o que estiver marcado.
 */
final class DuplicatesDialog {
	private DuplicatesDialog() {
	}

	static void show(final AppCompatActivity a) {
		final AppRepository repo = new ViewModelProvider(a).get(AppListModel.class).getAppRepository();
		final boolean[] cancelled = {false};
		final ProgressDialog pd = new ProgressDialog(a);
		pd.setTitle("Analisando o app");
		pd.setMessage("Preparando...");
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setCancelable(false);
		pd.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancelar", (d, w) -> cancelled[0] = true);
		pd.show();

		final Set<String> protectedFiles = new HashSet<>();
		String sel = ApexPrefs.shaderSel();
		if (sel.startsWith(ApexPrefs.SEL_USER)) {
			protectedFiles.add(ApexPrefs.userShaderFile(sel.substring(ApexPrefs.SEL_USER.length())).getPath());
		}

		new Thread(() -> {
			try {
				final DeepDuplicateScanner.Result res = DuplicateCleaner.analyze(repo, protectedFiles,
						new DeepDuplicateScanner.Progress() {
							@Override
							public void onPhase(String phase, int done, int total) {
								a.runOnUiThread(() -> {
									pd.setMessage(phase);
									pd.setIndeterminate(total <= 0);
									if (total > 0) {
										pd.setMax(total);
										pd.setProgress(done);
									}
								});
							}

							@Override
							public boolean isCancelled() {
								return cancelled[0];
							}
						});
				final File report = DuplicateCleaner.writeReport(res);
				a.runOnUiThread(() -> {
					pd.dismiss();
					if (!cancelled[0]) {
						results(a, repo, res, report);
					}
				});
			} catch (Throwable e) {
				a.runOnUiThread(() -> {
					pd.dismiss();
					Toast.makeText(a, "Erro na análise: " + e.getMessage(), Toast.LENGTH_LONG).show();
				});
			}
		}, "apex-dedupe").start();
	}

	/** Uma linha marcavel e o que ela apaga. */
	private static final class Row {
		final CheckBox box;
		final Group group;
		final Extra extra;
		final FileGroup files;

		Row(CheckBox box, Group group, Extra extra, FileGroup files) {
			this.box = box;
			this.group = group;
			this.extra = extra;
			this.files = files;
		}
	}

	private static CheckBox box(AppCompatActivity a, String text, boolean checked) {
		CheckBox c = new CheckBox(a);
		c.setText(text);
		c.setChecked(checked);
		c.setTextSize(12.5f);
		c.setTextColor(ApexTheme.textColor(a));
		ApexTheme.applyFont(c);
		return c;
	}

	private static TextView note(AppCompatActivity a, String text) {
		TextView t = ApexUi.text(a, text, 12);
		t.setAlpha(0.8f);
		t.setPadding(ApexUi.dp(a, 32), 0, 0, ApexUi.dp(a, 6));
		return t;
	}

	private static void results(final AppCompatActivity a, final AppRepository repo,
								final DeepDuplicateScanner.Result res, final File report) {
		if (a.isFinishing()) {
			return;
		}
		final List<Row> rows = new ArrayList<>();
		LinearLayout root = ApexUi.column(a);
		ScrollView sv = new ScrollView(a);
		sv.addView(root);

		long reclaim = 0;
		for (Group g : res.groups) {
			if (g.defaultChecked) {
				reclaim += g.bytes;
			}
		}
		int certain = 0;
		int suspect = 0;
		for (Group g : res.groups) {
			if (g.level == DeepDuplicateScanner.Level.SUSPECT) {
				suspect++;
			} else {
				certain++;
			}
		}
		long fileWaste = 0;
		int fileOnly = 0;
		for (FileGroup fg : res.fileGroups) {
			fileWaste += fg.wasted();
			if (!fg.deletable) {
				fileOnly++;
			}
		}
		root.addView(ApexUi.text(a, res.gamesScanned + " jogos analisados em "
				+ String.format(java.util.Locale.ROOT, "%.1f", res.millis / 1000.0) + " s\n"
				+ certain + " grupos de duplicatas, " + suspect + " suspeitas, "
				+ res.extras.size() + " pastas sem dono, " + res.fileGroups.size()
				+ " grupos de arquivos repetidos.\n"
				+ "Dá para liberar cerca de " + DeepDuplicateScanner.formatSize(reclaim)
				+ " só com os grupos certos."
				+ (res.gamesUnreadable > 0 ? "\n" + res.gamesUnreadable + " jogos com jar ilegível." : ""), 13));

		boolean any = false;
		for (int pass = 0; pass < 2; pass++) {
			boolean wantSuspect = pass == 1;
			boolean header = false;
			for (Group g : res.groups) {
				if ((g.level == DeepDuplicateScanner.Level.SUSPECT) != wantSuspect) {
					continue;
				}
				if (!header) {
					root.addView(ApexUi.section(a, wantSuspect
							? "Suspeitas (revise antes de marcar)" : "Jogos duplicados"));
					header = true;
				}
				any = true;
				CheckBox cb = box(a, g.keep.title + " — apaga " + g.remove.size() + " · " + g.level.label,
						g.defaultChecked);
				root.addView(cb);
				StringBuilder sb = new StringBuilder();
				sb.append("Motivo: ").append(g.reason).append("\nFica: ")
						.append(DeepDuplicateScanner.describe(g.keep));
				for (Game x : g.remove) {
					sb.append("\nSai: ").append(DeepDuplicateScanner.describe(x));
				}
				root.addView(note(a, sb.toString()));
				rows.add(new Row(cb, g, null, null));
			}
		}

		boolean header = false;
		for (Extra e : res.extras) {
			if (!header) {
				root.addView(ApexUi.section(a, "Pastas sem dono"));
				header = true;
			}
			any = true;
			boolean def = e.kind == ExtraKind.ORPHAN_APP && e.copyOf != null;
			CheckBox cb = box(a, e.dir.getName() + " — " + e.kind.label + " ("
					+ DeepDuplicateScanner.formatSize(e.bytes) + ")", def);
			root.addView(cb);
			root.addView(note(a, e.copyOf != null ? "É cópia de: " + e.copyOf.title
					: e.kind == ExtraKind.ORPHAN_CONFIG ? "Pode conter saves antigos — vem desmarcado."
					: "Não é cópia de nenhum jogo instalado — vem desmarcado."));
			rows.add(new Row(cb, null, e, null));
		}

		header = false;
		for (FileGroup fg : res.fileGroups) {
			if (!fg.deletable) {
				continue;
			}
			if (!header) {
				root.addView(ApexUi.section(a, "Shaders repetidos"));
				header = true;
			}
			any = true;
			CheckBox cb = box(a, fg.files.get(fg.keepIndex).getName() + " — " + (fg.files.size() - 1)
					+ " cópia(s) igual(is)", true);
			root.addView(cb);
			StringBuilder sb = new StringBuilder("Fica: " + fg.files.get(fg.keepIndex).getName());
			for (int i = 0; i < fg.files.size(); i++) {
				if (i != fg.keepIndex) {
					sb.append("\nSai: ").append(fg.files.get(i).getName());
				}
			}
			root.addView(note(a, sb.toString()));
			rows.add(new Row(cb, null, null, fg));
		}
		if (fileOnly > 0) {
			root.addView(ApexUi.section(a, "Outros arquivos repetidos"));
			root.addView(ApexUi.text(a, fileOnly + " grupos de arquivos iguais fora dos jogos (perfis, "
					+ "imagens, fontes...). São cópias de propósito, então só aparecem no relatório.", 12));
		}
		if (!any && fileOnly == 0) {
			root.addView(ApexUi.text(a, "\nNenhuma duplicata encontrada.", 14));
		}
		if (report != null) {
			root.addView(ApexUi.text(a, "\nRelatório completo salvo em:\n" + report.getPath(), 11));
		}

		ApexDialogBuilder b = new ApexDialogBuilder(a);
		b.setTitle("Duplicatas").setView(sv);
		if (!rows.isEmpty()) {
			b.setPositiveButton("Apagar marcados", (d, w) -> confirm(a, repo, rows));
		}
		b.setNegativeButton(R.string.apex_close, null);
		b.show();
	}

	private static void confirm(final AppCompatActivity a, final AppRepository repo, List<Row> rows) {
		final Set<Game> games = new LinkedHashSet<>();
		final List<Extra> extras = new ArrayList<>();
		final List<FileGroup> files = new ArrayList<>();
		for (Row r : rows) {
			if (!r.box.isChecked()) {
				continue;
			}
			if (r.group != null) {
				games.addAll(r.group.remove);
			} else if (r.extra != null) {
				extras.add(r.extra);
			} else if (r.files != null) {
				files.add(r.files);
			}
		}
		if (games.isEmpty() && extras.isEmpty() && files.isEmpty()) {
			Toast.makeText(a, "Nada marcado", Toast.LENGTH_SHORT).show();
			return;
		}
		long bytes = 0;
		int withSaves = 0;
		for (Game g : games) {
			bytes += g.dirSize + g.savesSize;
			if (g.savesSize > 0) {
				withSaves++;
			}
		}
		for (Extra e : extras) {
			bytes += e.bytes;
		}
		for (FileGroup fg : files) {
			bytes += fg.size * (fg.files.size() - 1);
		}
		String msg = games.size() + " jogos, " + extras.size() + " pastas e "
				+ files.size() + " grupos de shaders serão apagados (cerca de "
				+ DeepDuplicateScanner.formatSize(bytes) + ")."
				+ (withSaves > 0 ? "\n\n" + withSaves + " dos jogos apagados têm saves, que também serão perdidos."
				: "")
				+ "\n\nNão dá para desfazer.";
		new ApexDialogBuilder(a).setTitle("Apagar?").setMessage(msg)
				.setPositiveButton("Apagar", (d, w) -> {
					final ProgressDialog pd = new ProgressDialog(a);
					pd.setMessage("Apagando...");
					pd.setCancelable(false);
					pd.show();
					new Thread(() -> {
						int n = DuplicateCleaner.delete(repo, games, extras, files);
						ImportStore.sync();
						a.runOnUiThread(() -> {
							pd.dismiss();
							Toast.makeText(a, n + " itens apagados", Toast.LENGTH_LONG).show();
						});
					}, "apex-dedupe-del").start();
				})
				.setNegativeButton(android.R.string.cancel, null).show();
	}
}
