package ru.woesss.j2me.installer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.AppUtils;
import ru.playsoftware.j2meloader.util.FileUtils;

/**
 * Liga o {@link DeepDuplicateScanner} (motor, sem Android) ao app: monta a lista de jogos instalados,
 * as pastas a varrer e apaga o que o usuario marcou.
 */
public final class DuplicateCleaner {
	private DuplicateCleaner() {
	}

	/** Roda em thread de fundo. */
	public static DeepDuplicateScanner.Result analyze(AppRepository repo, Set<String> protectedFiles,
													  DeepDuplicateScanner.Progress progress) throws Exception {
		GameFingerprint.init(null);
		List<AppItem> items = new ArrayList<>(repo.getAll().blockingFirst());
		List<DeepDuplicateScanner.Game> games = new ArrayList<>(items.size());
		for (AppItem item : items) {
			games.add(new DeepDuplicateScanner.Game(item.getId(), item.getTitle(), item.getAuthor(),
					item.getVersion(), new File(item.getPathExt()),
					new File(Config.getSavesDir(item.getPath())),
					new File(Config.getConfigsDir(), item.getPath()), item));
		}

		DeepDuplicateScanner.Options opt = new DeepDuplicateScanner.Options();
		File appsDir = new File(Config.getAppDir());
		File configsDir = new File(Config.getConfigsDir());
		opt.appsDir = appsDir;
		opt.configsDir = configsDir;
		opt.deletableRoot = new File(Config.getShadersDir());
		if (protectedFiles != null) {
			opt.protectedPaths.addAll(protectedFiles);
		}
		// arquivos repetidos: tudo da pasta do app, menos o conteudo dos jogos (data/ e jogos/),
		// onde bibliotecas iguais em varios jogos sao normais
		File[] top = new File(Config.getEmulatorDir()).listFiles();
		if (top != null) {
			for (File f : top) {
				if (!f.equals(appsDir) && !f.equals(configsDir)
						&& !f.getName().equals(appsDir.getName()) && !f.getName().equals(configsDir.getName())) {
					opt.fileRoots.add(f);
				}
			}
		}

		DeepDuplicateScanner.Result res = DeepDuplicateScanner.scan(games, opt,
				new DeepDuplicateScanner.Hasher() {
					@Override
					public String contentHash(File jar) throws Exception {
						return GameFingerprint.hashOf(jar);
					}
				}, progress);
		GameFingerprint.flush();
		return res;
	}

	/** Grava o relatorio completo em configuracoes/duplicatas-relatorio.txt. Devolve o arquivo ou null. */
	public static File writeReport(DeepDuplicateScanner.Result res) {
		try {
			File dir = new File(Config.getSettingsDir());
			if (!dir.isDirectory() && !dir.mkdirs()) {
				return null;
			}
			File out = new File(dir, "duplicatas-relatorio.txt");
			try (java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
				os.write(DeepDuplicateScanner.report(res).getBytes("UTF-8"));
			}
			return out;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Apaga o que foi marcado: jogos (pasta, saves e configs + registro no banco), pastas sem dono e
	 * copias de arquivos. Devolve quantos itens foram apagados. Roda em thread de fundo.
	 */
	public static int delete(AppRepository repo, Collection<DeepDuplicateScanner.Game> games,
							 Collection<DeepDuplicateScanner.Extra> extras,
							 Collection<DeepDuplicateScanner.FileGroup> fileGroups) {
		int count = 0;
		List<AppItem> items = new ArrayList<>();
		for (DeepDuplicateScanner.Game g : games) {
			if (g.tag instanceof AppItem) {
				AppItem item = (AppItem) g.tag;
				AppUtils.deleteApp(item);
				items.add(item);
				count++;
			}
		}
		if (!items.isEmpty()) {
			repo.delete(items);
		}
		for (DeepDuplicateScanner.Extra e : extras) {
			if (FileUtils.deleteDirectory(e.dir)) {
				count++;
			}
		}
		for (DeepDuplicateScanner.FileGroup fg : fileGroups) {
			if (!fg.deletable) {
				continue;
			}
			for (int i = 0; i < fg.files.size(); i++) {
				if (i != fg.keepIndex && fg.files.get(i).delete()) {
					count++;
				}
			}
		}
		GameFingerprint.flush();
		return count;
	}
}
