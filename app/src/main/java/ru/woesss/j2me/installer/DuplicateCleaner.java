package ru.woesss.j2me.installer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.AppUtils;

/**
 * Analise profunda de duplicatas entre os jogos instalados (ja convertidos).
 * Dois jogos sao duplicatas se o conteudo do res.jar e identico OU se o titulo normalizado e igual.
 * Em cada grupo fica so o primeiro ja convertido (menor id, que tem converted.dex); o resto sai.
 */
public final class DuplicateCleaner {
	private DuplicateCleaner() {
	}

	public interface Progress {
		void onProgress(int done, int total);
	}

	public static final class Group {
		public final AppItem keep;
		public final List<AppItem> remove;
		/** true se algum removido tem o conteudo exatamente igual ao mantido. */
		public final boolean identical;

		Group(AppItem keep, List<AppItem> remove, boolean identical) {
			this.keep = keep;
			this.remove = remove;
			this.identical = identical;
		}
	}

	private static boolean converted(AppItem item) {
		return new File(item.getPathExt(), Config.MIDLET_DEX_FILE).isFile();
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	/** Roda em thread de fundo. */
	public static List<Group> analyze(AppRepository repo, Progress progress) throws Exception {
		GameFingerprint.init(null);
		List<AppItem> all = new ArrayList<>(repo.getAll().blockingFirst());
		Collections.sort(all, new Comparator<AppItem>() {
			@Override
			public int compare(AppItem x, AppItem y) {
				return Integer.compare(x.getId(), y.getId());
			}
		});
		int n = all.size();
		int[] parent = new int[n];
		String[] hashes = new String[n];
		for (int i = 0; i < n; i++) {
			parent[i] = i;
			File jar = new File(all.get(i).getPathExt(), Config.MIDLET_RES_FILE);
			if (jar.isFile()) {
				try {
					hashes[i] = GameFingerprint.hashOf(jar);
				} catch (Exception ignored) {
				}
			}
			if (progress != null) {
				progress.onProgress(i + 1, n);
			}
		}
		Map<String, Integer> byHash = new HashMap<>();
		Map<String, Integer> byTitle = new HashMap<>();
		for (int i = 0; i < n; i++) {
			if (hashes[i] != null) {
				Integer j = byHash.get(hashes[i]);
				if (j == null) {
					byHash.put(hashes[i], i);
				} else {
					parent[find(parent, i)] = find(parent, j);
				}
			}
			String t = GameFingerprint.normalizeTitle(all.get(i).getTitle());
			if (!t.isEmpty()) {
				Integer j = byTitle.get(t);
				if (j == null) {
					byTitle.put(t, i);
				} else {
					parent[find(parent, i)] = find(parent, j);
				}
			}
		}
		Map<Integer, List<Integer>> groups = new HashMap<>();
		for (int i = 0; i < n; i++) {
			int r = find(parent, i);
			List<Integer> g = groups.get(r);
			if (g == null) {
				g = new ArrayList<>();
				groups.put(r, g);
			}
			g.add(i);
		}
		List<Group> result = new ArrayList<>();
		for (List<Integer> g : groups.values()) {
			if (g.size() < 2) {
				continue;
			}
			int keepIdx = -1;
			for (int i : g) { // g ja esta em ordem de id
				if (converted(all.get(i))) {
					keepIdx = i;
					break;
				}
			}
			if (keepIdx < 0) {
				continue; // nenhum convertido: nao mexe
			}
			List<AppItem> remove = new ArrayList<>();
			boolean identical = false;
			for (int i : g) {
				if (i == keepIdx) {
					continue;
				}
				remove.add(all.get(i));
				if (hashes[i] != null && hashes[i].equals(hashes[keepIdx])) {
					identical = true;
				}
			}
			result.add(new Group(all.get(keepIdx), remove, identical));
		}
		Collections.sort(result, new Comparator<Group>() {
			@Override
			public int compare(Group x, Group y) {
				return x.keep.getTitle().compareToIgnoreCase(y.keep.getTitle());
			}
		});
		return result;
	}

	/** Apaga os duplicados (pasta, saves e configs) e tira do banco. Roda em thread de fundo. */
	public static int delete(AppRepository repo, List<Group> groups) {
		List<AppItem> all = new ArrayList<>();
		for (Group g : groups) {
			for (AppItem item : g.remove) {
				AppUtils.deleteApp(item);
				all.add(item);
			}
		}
		if (!all.isEmpty()) {
			repo.delete(all);
		}
		GameFingerprint.flush();
		return all.size();
	}
}
