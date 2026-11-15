package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.app.ProgressDialog;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.woesss.j2me.installer.DuplicateCleaner;

/** Botao "Duplicatas": analisa todos os jogos e apaga as copias, deixando so o primeiro ja convertido. */
final class DuplicatesDialog {
	private DuplicatesDialog() {
	}

	static void show(final AppCompatActivity a) {
		final AppRepository repo = new ViewModelProvider(a).get(AppListModel.class).getAppRepository();
		final ProgressDialog pd = new ProgressDialog(a);
		pd.setTitle("Analisando jogos");
		pd.setMessage("Análise profunda do conteúdo e dos títulos...");
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setCancelable(false);
		pd.show();
		new Thread(() -> {
			try {
				List<DuplicateCleaner.Group> groups = DuplicateCleaner.analyze(repo, (done, total) ->
						a.runOnUiThread(() -> {
							pd.setMax(total);
							pd.setProgress(done);
						}));
				a.runOnUiThread(() -> {
					pd.dismiss();
					confirm(a, repo, groups);
				});
			} catch (Throwable e) {
				a.runOnUiThread(() -> {
					pd.dismiss();
					Toast.makeText(a, "Erro na análise: " + e.getMessage(), Toast.LENGTH_LONG).show();
				});
			}
		}, "apex-dedupe").start();
	}

	private static void confirm(final AppCompatActivity a, final AppRepository repo,
								final List<DuplicateCleaner.Group> groups) {
		if (a.isFinishing()) {
			return;
		}
		if (groups.isEmpty()) {
			new ApexDialogBuilder(a).setTitle("Duplicatas")
					.setMessage("Nenhum jogo duplicado encontrado.")
					.setPositiveButton(android.R.string.ok, null).show();
			return;
		}
		int total = 0;
		StringBuilder sb = new StringBuilder();
		for (DuplicateCleaner.Group g : groups) {
			total += g.remove.size();
			sb.append("• ").append(g.keep.getTitle()).append(" — fica 1, apaga ").append(g.remove.size())
					.append(g.identical ? "" : " (mesmo título/ícone)").append('\n');
		}
		final int count = total;
		new ApexDialogBuilder(a).setTitle(count + " duplicatas em " + groups.size() + " jogos")
				.setMessage(sb + "\nFica só o primeiro já convertido de cada jogo. Os apagados perdem também "
						+ "saves e configurações. Apagar?")
				.setPositiveButton("Apagar", (d, w) -> {
					final ProgressDialog pd = new ProgressDialog(a);
					pd.setMessage("Apagando...");
					pd.setCancelable(false);
					pd.show();
					new Thread(() -> {
						int n = DuplicateCleaner.delete(repo, groups);
						a.runOnUiThread(() -> {
							pd.dismiss();
							Toast.makeText(a, n + " duplicatas apagadas", Toast.LENGTH_LONG).show();
						});
					}, "apex-dedupe-del").start();
				})
				.setNegativeButton(android.R.string.cancel, null).show();
	}
}
