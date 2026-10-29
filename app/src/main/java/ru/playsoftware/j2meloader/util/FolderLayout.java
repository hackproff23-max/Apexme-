package ru.playsoftware.j2meloader.util;

import android.util.Log;

import java.io.File;

import ru.playsoftware.j2meloader.config.Config;

/**
 * Estrutura da pasta Apexme:
 * <pre>
 * Apexme/
 *   jogos/          uma pasta por jogo: config.json, VirtualKeyboardLayout e saves/
 *   configuracoes/  cores do tema (tema.json), logs, drive interno (fs/) e cache
 *   importacao/     tudo que o usuario importa (PNG de fundo, fontes...)
 *   shaders/        shaders
 *   perfil/         perfis de configuracao para jogos
 *   data/           jogos instalados (convertidos)
 * </pre>
 * Tambem migra a estrutura antiga (converted, configs, data de saves, templates, fs, cache).
 */
public final class FolderLayout {
	private static final String TAG = FolderLayout.class.getSimpleName();

	private FolderLayout() {
	}

	/** Migra a estrutura antiga (se existir) e garante que as 6 pastas existam. */
	public static void prepare(File root) {
		try {
			migrateOld(root);
		} catch (Throwable e) {
			Log.e(TAG, "Falha ao migrar pastas antigas", e);
		}
		String[] dirs = {
				Config.MIDLET_CONFIGS_DIR,
				Config.SETTINGS_DIR,
				Config.IMPORT_DIR,
				Config.SHADERS_DIR,
				Config.PROFILES_DIR,
				Config.APPS_DIR,
		};
		for (String d : dirs) {
			File f = new File(root + d);
			if (!f.isDirectory()) {
				//noinspection ResultOfMethodCallIgnored
				f.mkdirs();
			}
		}
	}

	private static void migrateOld(File root) {
		File oldConverted = new File(root, "converted");
		File oldConfigs = new File(root, "configs");
		File oldTemplates = new File(root, "templates");
		File oldFs = new File(root, "fs");
		File oldCache = new File(root, "cache");
		File oldData = new File(root, "data");
		File games = new File(root + Config.MIDLET_CONFIGS_DIR);
		File newData = new File(root + Config.APPS_DIR);

		// "converted" (jogos instalados) vira "data". A antiga "data" guardava os saves.
		if (oldConverted.isDirectory()) {
			File oldSaves = null;
			if (oldData.isDirectory()) {
				oldSaves = new File(root, ".data_antigo");
				if (oldSaves.exists()) {
					oldSaves = new File(root, ".data_antigo_" + System.currentTimeMillis());
				}
				if (!oldData.renameTo(oldSaves)) {
					Log.e(TAG, "Nao foi possivel mover " + oldData);
					return;
				}
			}
			if (!oldConverted.renameTo(newData)) {
				Log.e(TAG, "Nao foi possivel mover " + oldConverted);
				if (oldSaves != null) {
					//noinspection ResultOfMethodCallIgnored
					oldSaves.renameTo(oldData);
				}
				return;
			}
			if (oldSaves != null) {
				File[] list = oldSaves.listFiles();
				if (list != null) {
					for (File saveDir : list) {
						if (!saveDir.isDirectory()) {
							continue;
						}
						File dst = new File(games, saveDir.getName() + Config.MIDLET_SAVES_DIR);
						if (!dst.exists()) {
							//noinspection ResultOfMethodCallIgnored
							dst.getParentFile().mkdirs();
							//noinspection ResultOfMethodCallIgnored
							saveDir.renameTo(dst);
						}
					}
				}
				//noinspection ResultOfMethodCallIgnored
				oldSaves.delete(); // so apaga se estiver vazia
			}
		}
		if (oldConfigs.isDirectory()) {
			mergeInto(oldConfigs, games);
		}
		if (oldTemplates.isDirectory()) {
			mergeInto(oldTemplates, new File(root + Config.PROFILES_DIR));
		}
		if (oldFs.isDirectory()) {
			mergeInto(oldFs, new File(root + Config.FS_DIR));
		}
		if (oldCache.isDirectory()) {
			FileUtils.deleteDirectory(oldCache);
		}
	}

	/** Move o conteudo de src para dst sem sobrescrever nada; apaga src se ficar vazia. */
	private static void mergeInto(File src, File dst) {
		if (!dst.isDirectory() && !dst.mkdirs()) {
			return;
		}
		File[] list = src.listFiles();
		if (list != null) {
			for (File child : list) {
				File target = new File(dst, child.getName());
				if (!target.exists()) {
					//noinspection ResultOfMethodCallIgnored
					child.renameTo(target);
				} else if (child.isDirectory() && target.isDirectory()) {
					mergeInto(child, target);
				}
			}
		}
		//noinspection ResultOfMethodCallIgnored
		src.delete();
	}
}
