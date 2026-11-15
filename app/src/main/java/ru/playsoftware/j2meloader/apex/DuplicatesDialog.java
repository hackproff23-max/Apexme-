package ru.playsoftware.j2meloader.apex;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
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

	/** Uma copia dentro de um grupo, com a caixa "apagar". */
	private static final class Member {
		final Game game;
		final CheckBox box;

		Member(Game game, CheckBox box) {
			this.game = game;
			this.box = box;
		}
	}

	/** Uma linha marcavel e o que ela apaga. */
	private static final class Row {
		final CheckBox box;
		final Group group;
		final List<Member> members;
		final Extra extra;
		final FileGroup files;

		Row(CheckBox box, Group group, List<Member> members, Extra extra, FileGroup files) {
			this.box = box;
			this.group = group;
			this.members = members;
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

	private static Bitmap icon(Game g) {
		try {
			File f = new File(g.dir, "icon.png");
			if (!f.isFile()) {
				return null;
			}
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(f.getPath(), o);
			int sample = 1;
			while (o.outWidth / sample > 128 || o.outHeight / sample > 128) {
				sample *= 2;
			}
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = sample;
			return BitmapFactory.decodeFile(f.getPath(), o2);
		} catch (Throwable t) {
			return null;
		}
	}

	private static String headerText(Group g, List<Member> members, boolean open) {
		int del = 0;
		for (Member m : members) {
			if (m.box.isChecked()) {
				del++;
			}
		}
		return (open ? "▼ " : "▶ ") + g.keep.title + "\n    " + members.size() + " cópias · "
				+ g.level.label + " · apagar " + del;
	}

	/** Linha do grupo: toque abre embaixo as copias, cada uma com icone, nome completo e caixa "apagar". */
	private static Row groupRow(final AppCompatActivity a, LinearLayout root, final Group g) {
		final List<Member> members = new ArrayList<>();
		final LinearLayout body = new LinearLayout(a);
		body.setOrientation(LinearLayout.VERTICAL);
		body.setVisibility(View.GONE);
		final TextView head = ApexUi.text(a, "", 14);
		head.setTypeface(head.getTypeface(), Typeface.BOLD);
		head.setPadding(ApexUi.dp(a, 4), ApexUi.dp(a, 8), ApexUi.dp(a, 4), ApexUi.dp(a, 8));
		head.setClickable(true);
		final boolean[] open = {false};
		final Runnable refresh = () -> head.setText(headerText(g, members, open[0]));

		List<Game> all = new ArrayList<>();
		all.add(g.keep);
		all.addAll(g.remove);
		for (final Game x : all) {
			boolean suggested = g.remove.contains(x) && g.defaultChecked;
			LinearLayout item = new LinearLayout(a);
			item.setOrientation(LinearLayout.VERTICAL);
			item.setPadding(ApexUi.dp(a, 8), ApexUi.dp(a, 6), ApexUi.dp(a, 4), ApexUi.dp(a, 6));

			LinearLayout top = new LinearLayout(a);
			top.setOrientation(LinearLayout.HORIZONTAL);
			top.setGravity(Gravity.CENTER_VERTICAL);
			ImageView iv = new ImageView(a);
			iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
			Bitmap bmp = icon(x);
			if (bmp != null) {
				iv.setImageBitmap(bmp);
			}
			top.addView(iv, new LinearLayout.LayoutParams(ApexUi.dp(a, 44), ApexUi.dp(a, 44)));
			StringBuilder sb = new StringBuilder(x.title);
			sb.append("\n").append(x.version.isEmpty() ? "" : "v" + x.version + " · ")
					.append(x.author.isEmpty() ? "" : x.author + " · ")
					.append(DeepDuplicateScanner.formatSize(x.dirSize));
			sb.append("\n").append(x.converted ? "convertido" : "NÃO convertido");
			if (x.savesSize > 0) {
				sb.append(" · saves ").append(DeepDuplicateScanner.formatSize(x.savesSize));
			}
			sb.append("\npasta: ").append(x.dir.getName());
			if (x == g.keep) {
				sb.append("\n(sugestão: ficar)");
			}
			TextView info = ApexUi.text(a, sb.toString(), 12);
			LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0,
					LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			ip.setMarginStart(ApexUi.dp(a, 8));
			top.addView(info, ip);
			item.addView(top);

			final CheckBox cb = box(a, "Apagar este", suggested);
			item.addView(cb);
			final Member m = new Member(x, cb);
			members.add(m);
			cb.setOnClickListener(v -> {
				boolean allMarked = true;
				for (Member o : members) {
					allMarked &= o.box.isChecked();
				}
				if (allMarked) { // nunca apagar todas as copias
					cb.setChecked(false);
					Toast.makeText(a, "Pelo menos uma cópia precisa ficar", Toast.LENGTH_SHORT).show();
				}
				refresh.run();
			});
			body.addView(item);
		}
		TextView why = note(a, "Motivo: " + g.reason);
		why.setPadding(ApexUi.dp(a, 8), 0, 0, ApexUi.dp(a, 4));
		body.addView(why, 0);

		head.setOnClickListener(v -> {
			open[0] = !open[0];
			body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
			refresh.run();
		});
		refresh.run();
		root.addView(head);
		root.addView(body);
		return new Row(null, g, members, null, null);
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
							? "Suspeitas (toque para escolher qual apagar)"
							: "Jogos duplicados (toque para ver as cópias)"));
					header = true;
				}
				any = true;
				rows.add(groupRow(a, root, g));
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
			rows.add(new Row(cb, null, null, e, null));
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
			rows.add(new Row(cb, null, null, null, fg));
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
			if (r.members != null) {
				for (Member m : r.members) {
					if (m.box.isChecked()) {
						games.add(m.game);
					}
				}
			} else if (r.box != null && r.box.isChecked()) {
				if (r.extra != null) {
					extras.add(r.extra);
				} else if (r.files != null) {
					files.add(r.files);
				}
			}
		}
		// guarda: em nenhum grupo pode sumir todas as copias (um grupo pode compartilhar jogos com outro)
		for (Row r : rows) {
			if (r.members == null) {
				continue;
			}
			boolean survivor = false;
			for (Member m : r.members) {
				survivor |= !games.contains(m.game);
			}
			if (!survivor) {
				Toast.makeText(a, "\"" + r.group.keep.title + "\": pelo menos uma cópia precisa ficar",
						Toast.LENGTH_LONG).show();
				return;
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
