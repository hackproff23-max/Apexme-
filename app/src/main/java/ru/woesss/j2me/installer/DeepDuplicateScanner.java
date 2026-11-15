/*
 * Copyright 2026 Apexme contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.woesss.j2me.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scanner de duplicatas em varias camadas. Nao depende do Android (so de java.*), para poder ser
 * testado na JVM.
 *
 * <h3>O que ele compara entre os jogos instalados</h3>
 * <ol>
 *   <li>Conteudo do jar (hash dos arquivos descompactados, ignora manifesto e ordem) - "identico".</li>
 *   <li>Impressao digital de cada entrada do jar (CRC32 + tamanho, sem descompactar): mede quanto dos
 *       arquivos dois jogos compartilham, mesmo com nomes diferentes (outra resolucao, idioma, repack).</li>
 *   <li>Parte so das classes (.class): mesmo codigo com recursos diferentes.</li>
 *   <li>Identidade do manifesto: MIDlet-Name + fornecedor + classe do MIDlet-1.</li>
 *   <li>Icone (hash) + autor.</li>
 *   <li>Titulo normalizado igual e titulo parecido (tokens/Levenshtein), sem confundir sequencias (1, 2, II).</li>
 * </ol>
 * Cada par de jogos recebe uma pontuacao somando as evidencias. Pontuacao alta forma grupos "certos";
 * pontuacao media forma "suspeitas" (vem desmarcadas para o usuario revisar).
 *
 * <h3>Alem dos jogos</h3>
 * pastas de jogos sem registro (orfas), pastas de configuracao/saves sem jogo, e arquivos repetidos
 * de verdade (tamanho -> amostra -> SHA-256) em todas as pastas do app, menos dentro dos jogos.
 */
public final class DeepDuplicateScanner {
	private DeepDuplicateScanner() {
	}

	// ------------------------------------------------------------------ modelo

	public enum Level {
		IDENTICAL("Idêntico"),
		SAME_CODE("Mesmo código/arquivos"),
		SAME_GAME("Mesmo jogo"),
		SUSPECT("Suspeita - revise");

		public final String label;

		Level(String label) {
			this.label = label;
		}
	}

	/** Um jogo instalado, ja com os dados medidos pelo scanner. */
	public static final class Game {
		public final int id;
		public final String title;
		public final String author;
		public final String version;
		/** Pasta do jogo (onde ficam res.jar, converted.dex e icon.png). */
		public final File dir;
		/** Pasta de saves do jogo (pode nao existir). */
		public final File savesDir;
		/** Pasta de configuracao do jogo (pode nao existir). */
		public final File configDir;
		/** Objeto de origem (AppItem), devolvido ao apagar. */
		public final Object tag;

		public File jar;
		public boolean converted;
		public boolean hasIcon;
		public long dirSize;
		public long savesSize;
		public long lastSave;
		public boolean hasConfig;
		public boolean unreadable;

		String normTitle = "";
		String contentHash;
		String iconHash;
		String manifestKey;
		long[] entries = new long[0];
		long[] classes = new long[0];
		Set<String> tokens = Collections.emptySet();
		Set<String> numberTokens = Collections.emptySet();

		public Game(int id, String title, String author, String version, File dir, File savesDir,
					File configDir, Object tag) {
			this.id = id;
			this.title = title == null ? "" : title;
			this.author = author == null ? "" : author;
			this.version = version == null ? "" : version;
			this.dir = dir;
			this.savesDir = savesDir;
			this.configDir = configDir;
			this.tag = tag;
		}

		@Override
		public String toString() {
			return title + " (#" + id + ")";
		}
	}

	public static final class Group {
		public final Level level;
		public final Game keep;
		public final List<Game> remove;
		/** Motivos (do par mais forte do grupo), em texto. */
		public final String reason;
		/** Pontuacao do par mais forte. */
		public final int score;
		/** Vem marcado por padrao? (Suspeitas vem desmarcadas.) */
		public final boolean defaultChecked;
		/** Soma do tamanho (jogo + saves) do que sera apagado. */
		public final long bytes;

		Group(Level level, Game keep, List<Game> remove, String reason, int score) {
			this.level = level;
			this.keep = keep;
			this.remove = remove;
			this.reason = reason;
			this.score = score;
			this.defaultChecked = level != Level.SUSPECT;
			long b = 0;
			for (Game g : remove) {
				b += g.dirSize + g.savesSize;
			}
			this.bytes = b;
		}
	}

	public enum ExtraKind {
		/** Pasta em data/ sem registro no banco. */
		ORPHAN_APP("Pasta de jogo sem registro"),
		/** Pasta em jogos/ (config + saves) sem jogo instalado. */
		ORPHAN_CONFIG("Config/saves de jogo que nao existe mais");

		public final String label;

		ExtraKind(String label) {
			this.label = label;
		}
	}

	public static final class Extra {
		public final ExtraKind kind;
		public final File dir;
		public final long bytes;
		/** Se a pasta orfa e copia de um jogo instalado, qual. */
		public final Game copyOf;

		Extra(ExtraKind kind, File dir, long bytes, Game copyOf) {
			this.kind = kind;
			this.dir = dir;
			this.bytes = bytes;
			this.copyOf = copyOf;
		}
	}

	public static final class FileGroup {
		public final List<File> files;
		public final long size;
		/** Pode apagar as copias (so shaders)? Senao, so entra no relatorio. */
		public final boolean deletable;
		/** Indice do arquivo que fica. */
		public final int keepIndex;

		FileGroup(List<File> files, long size, boolean deletable, int keepIndex) {
			this.files = files;
			this.size = size;
			this.deletable = deletable;
			this.keepIndex = keepIndex;
		}

		public long wasted() {
			return size * (files.size() - 1);
		}
	}

	public static final class Result {
		public final List<Group> groups = new ArrayList<>();
		public final List<Extra> extras = new ArrayList<>();
		public final List<FileGroup> fileGroups = new ArrayList<>();
		public int gamesScanned;
		public int gamesUnreadable;
		public int pairsEvaluated;
		public long millis;
		public final List<String> errors = new ArrayList<>();

		public int removableGames() {
			Set<Game> s = new HashSet<>();
			for (Group g : groups) {
				s.addAll(g.remove);
			}
			return s.size();
		}
	}

	public static final class Options {
		/** Pasta com os jogos (data/). Usada para achar pastas orfas. Pode ser null. */
		public File appsDir;
		/** Pasta com config/saves por jogo (jogos/). Pode ser null. */
		public File configsDir;
		/** Pastas onde procurar arquivos repetidos (importacao/, shaders/, perfil/...). */
		public List<File> fileRoots = new ArrayList<>();
		/** Pasta cujos arquivos repetidos podem ser apagados (shaders/). */
		public File deletableRoot;
		/** Arquivos que nunca devem ser apagados (ex.: shader em uso). */
		public Set<String> protectedPaths = new HashSet<>();
		/** Quantas threads usar. */
		public int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
		/** Pontuacao minima para dois jogos entrarem na mesma suspeita/grupo. */
		public int weakThreshold = 35;
		public int strongThreshold = 60;
	}

	public interface Hasher {
		/** Hash do conteudo descompactado do jar (ignora manifesto/ordem). */
		String contentHash(File jar) throws Exception;
	}

	public interface Progress {
		void onPhase(String phase, int done, int total);

		boolean isCancelled();
	}

	// ------------------------------------------------------------------ analise

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern BY = Pattern.compile("\\bby\\s+.*$");
	private static final Pattern SIZES = Pattern.compile(
			"\\b(240x320|320x240|360x640|480x800|176x220|128x160|128x128|176x208|208x208|240x240|hd|sd|mod|remake|edition|versao|version|java|jar|jad|mobile|game|jogo)\\b");
	private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final Pattern ROMAN = Pattern.compile("^(i{1,3}|iv|v|vi{1,3}|ix|x)$");

	public static String normalizeTitle(String value) {
		if (value == null) {
			return "";
		}
		String s = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		s = MARKS.matcher(s).replaceAll("");
		s = BY.matcher(s).replaceAll(" ");
		s = SIZES.matcher(s).replaceAll(" ");
		s = NON_ALNUM.matcher(s).replaceAll(" ");
		s = s.replace("homem aranha", "spider man").replace("spiderman", "spider man");
		return SPACES.matcher(s.trim()).replaceAll(" ");
	}

	public static Result scan(List<Game> games, Options opt, Hasher hasher, Progress progress) {
		long t0 = System.currentTimeMillis();
		Result res = new Result();
		if (progress == null) {
			progress = new Progress() {
				@Override
				public void onPhase(String phase, int done, int total) {
				}

				@Override
				public boolean isCancelled() {
					return false;
				}
			};
		}
		List<Game> all = new ArrayList<>(games);
		Collections.sort(all, new Comparator<Game>() {
			@Override
			public int compare(Game a, Game b) {
				return Integer.compare(a.id, b.id);
			}
		});
		res.gamesScanned = all.size();
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, opt.threads));
		try {
			measure(all, hasher, pool, progress, res);
			if (progress.isCancelled()) {
				return res;
			}
			progress.onPhase("Comparando jogos", 0, 1);
			buildGroups(all, opt, res, progress);
			if (progress.isCancelled()) {
				return res;
			}
			findOrphans(all, opt, hasher, res, progress);
			if (progress.isCancelled()) {
				return res;
			}
			findFileDuplicates(opt, pool, res, progress);
		} catch (Throwable e) {
			res.errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
		} finally {
			pool.shutdownNow();
			res.millis = System.currentTimeMillis() - t0;
		}
		return res;
	}

	// ---- fase 1: medir cada jogo (em paralelo)

	private static void measure(final List<Game> all, final Hasher hasher, ExecutorService pool,
								final Progress progress, final Result res) throws Exception {
		final AtomicInteger done = new AtomicInteger();
		final int total = all.size();
		List<Future<?>> futures = new ArrayList<>();
		for (final Game g : all) {
			futures.add(pool.submit(new Callable<Void>() {
				@Override
				public Void call() {
					if (!progress.isCancelled()) {
						measureOne(g, hasher);
					}
					progress.onPhase("Lendo jogos", done.incrementAndGet(), total);
					return null;
				}
			}));
		}
		for (Future<?> f : futures) {
			f.get();
		}
		for (Game g : all) {
			if (g.unreadable) {
				res.gamesUnreadable++;
			}
		}
	}

	private static void measureOne(Game g, Hasher hasher) {
		g.normTitle = normalizeTitle(g.title);
		List<String> toks = new ArrayList<>();
		Set<String> nums = new LinkedHashSet<>();
		for (String t : g.normTitle.split(" ")) {
			if (t.isEmpty()) {
				continue;
			}
			if (isNumberToken(t)) {
				nums.add(numberValue(t));
			} else {
				toks.add(t);
			}
		}
		g.tokens = new HashSet<>(toks);
		g.numberTokens = nums;

		g.jar = new File(g.dir, "res.jar");
		g.converted = new File(g.dir, "converted.dex").isFile();
		File icon = new File(g.dir, "icon.png");
		g.hasIcon = icon.isFile() && icon.length() > 0;
		g.dirSize = sizeOf(g.dir);
		if (g.savesDir != null && g.savesDir.isDirectory()) {
			g.savesSize = sizeOf(g.savesDir);
			g.lastSave = newest(g.savesDir);
		}
		g.hasConfig = g.configDir != null && new File(g.configDir, "config.json").isFile();

		if (g.hasIcon && icon.length() <= 4L * 1024 * 1024) {
			try {
				g.iconHash = sha256(icon);
			} catch (Exception ignored) {
			}
		}
		if (g.jar.isFile()) {
			try {
				readZip(g);
			} catch (Exception e) {
				g.unreadable = true;
			}
			try {
				g.contentHash = hasher.contentHash(g.jar);
			} catch (Exception e) {
				g.unreadable = true;
			}
		} else {
			g.unreadable = true;
		}
	}

	/** Le so o indice (diretorio central) do zip: CRC32 + tamanho de cada entrada, sem descompactar. */
	private static void readZip(Game g) throws Exception {
		List<Long> all = new ArrayList<>();
		List<Long> cls = new ArrayList<>();
		String mname = null;
		try (ZipFile zip = new ZipFile(g.jar)) {
			java.util.Enumeration<? extends ZipEntry> en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				if (e.isDirectory()) {
					continue;
				}
				String name = e.getName();
				if ("META-INF/MANIFEST.MF".equalsIgnoreCase(name)) {
					mname = manifestIdentity(zip, e);
					continue;
				}
				long size = e.getSize();
				if (size <= 0) {
					continue;
				}
				long key = ((e.getCrc() & 0xffffffffL) << 32) | (size & 0xffffffffL);
				all.add(key);
				if (name.endsWith(".class")) {
					cls.add(key);
				}
			}
		}
		g.entries = sortedUnique(all);
		g.classes = sortedUnique(cls);
		g.manifestKey = mname;
	}

	/** MIDlet-Name | MIDlet-Vendor | classe do MIDlet-1. Null se faltar o essencial. */
	private static String manifestIdentity(ZipFile zip, ZipEntry e) {
		try (InputStream in = zip.getInputStream(e)) {
			byte[] buf = new byte[(int) Math.min(64 * 1024, Math.max(0, e.getSize()))];
			int off = 0;
			int n;
			while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) {
				off += n;
			}
			String text = new String(buf, 0, off, "UTF-8").replace("\r\n", "\n").replace("\r", "\n")
					.replace("\n ", ""); // continuacao de linha
			String name = null;
			String vendor = "";
			String midlet = null;
			for (String line : text.split("\n")) {
				int c = line.indexOf(':');
				if (c <= 0) {
					continue;
				}
				String k = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
				String v = line.substring(c + 1).trim();
				if (k.equals("midlet-name")) {
					name = v;
				} else if (k.equals("midlet-vendor")) {
					vendor = v;
				} else if (k.equals("midlet-1")) {
					String[] p = v.split(",");
					if (p.length >= 3) {
						midlet = p[2].trim();
					}
				}
			}
			if (name == null || name.isEmpty() || midlet == null || midlet.isEmpty()) {
				return null;
			}
			return normalizeTitle(name) + "|" + vendor.trim().toLowerCase(Locale.ROOT) + "|" + midlet;
		} catch (Exception ex) {
			return null;
		}
	}

	// ---- fase 2: pontuar pares e formar grupos

	private static final class Pair {
		int score;
		final List<String> reasons = new ArrayList<>(4);
		int best;
		String bestReason = "";

		void add(int pts, String reason) {
			score += pts;
			reasons.add(reason);
			if (pts > best) {
				best = pts;
				bestReason = reason;
			}
		}
	}

	private static long pairKey(int i, int j, int n) {
		return i < j ? (long) i * n + j : (long) j * n + i;
	}

	private static Pair pair(Map<Long, Pair> pairs, int i, int j, int n) {
		long k = pairKey(i, j, n);
		Pair p = pairs.get(k);
		if (p == null) {
			p = new Pair();
			pairs.put(k, p);
		}
		return p;
	}

	private static void buildGroups(List<Game> all, Options opt, Result res, Progress progress) {
		final int n = all.size();
		Map<Long, Pair> pairs = new HashMap<>();

		// iguais exatos: conteudo, manifesto, icone+autor, titulo
		Map<String, List<Integer>> byContent = new HashMap<>();
		Map<String, List<Integer>> byManifest = new HashMap<>();
		Map<String, List<Integer>> byIcon = new HashMap<>();
		Map<String, List<Integer>> byTitle = new HashMap<>();
		for (int i = 0; i < n; i++) {
			Game g = all.get(i);
			put(byContent, g.contentHash, i);
			put(byManifest, g.manifestKey, i);
			if (g.iconHash != null) {
				put(byIcon, g.iconHash + "|" + g.author.trim().toLowerCase(Locale.ROOT), i);
			}
			if (!g.normTitle.isEmpty()) {
				put(byTitle, g.normTitle, i);
			}
		}
		exact(byContent, pairs, n, 100, "conteúdo idêntico");
		exact(byManifest, pairs, n, 50, "mesmo MIDlet (nome/fornecedor/classe)");
		exact(byIcon, pairs, n, 40, "mesmo ícone e autor");
		exact(byTitle, pairs, n, 35, "mesmo título");

		// arquivos compartilhados (indice invertido; ignora entradas muito comuns)
		Map<Long, List<Integer>> index = new HashMap<>();
		for (int i = 0; i < n; i++) {
			for (long k : all.get(i).entries) {
				List<Integer> l = index.get(k);
				if (l == null) {
					l = new ArrayList<>(2);
					index.put(k, l);
				}
				l.add(i);
			}
		}
		final int cap = Math.max(12, (int) Math.sqrt(n) * 3);
		Map<Long, Integer> shared = new HashMap<>();
		for (List<Integer> l : index.values()) {
			int sz = l.size();
			if (sz < 2 || sz > cap) {
				continue;
			}
			for (int a = 0; a < sz; a++) {
				for (int b = a + 1; b < sz; b++) {
					long k = pairKey(l.get(a), l.get(b), n);
					Integer c = shared.get(k);
					shared.put(k, c == null ? 1 : c + 1);
				}
			}
		}
		index = null;
		int evaluated = 0;
		for (Map.Entry<Long, Integer> e : shared.entrySet()) {
			int i = (int) (e.getKey() / n);
			int j = (int) (e.getKey() % n);
			Game a = all.get(i);
			Game b = all.get(j);
			int minSize = Math.min(a.entries.length, b.entries.length);
			if (minSize == 0 || e.getValue() < 2 && minSize > 2) {
				continue;
			}
			int inter = intersect(a.entries, b.entries);
			if (inter == 0) {
				continue;
			}
			evaluated++;
			double jac = inter / (double) (a.entries.length + b.entries.length - inter);
			double contain = inter / (double) minSize;
			int cInter = intersect(a.classes, b.classes);
			int cMin = Math.min(a.classes.length, b.classes.length);
			double cJac = cMin == 0 ? 0 : cInter / (double) (a.classes.length + b.classes.length - cInter);
			Pair p = pair(pairs, i, j, n);
			if (jac >= 0.90) {
				p.add(90, pct(jac) + " dos arquivos iguais");
			} else if (jac >= 0.60 && cJac >= 0.97 && cMin >= 2) {
				p.add(80, "mesmo código, " + pct(jac) + " dos arquivos iguais");
			} else if (contain >= 0.97 && inter >= 10 && jac >= 0.5) {
				p.add(70, "um jogo contém o outro (" + pct(contain) + ")");
			} else if (jac >= 0.40) {
				p.add(30, pct(jac) + " dos arquivos em comum");
			} else if (jac >= 0.20 && cJac >= 0.97 && cMin >= 2) {
				p.add(25, "mesmo código, poucos arquivos em comum");
			}
		}
		res.pairsEvaluated = evaluated;
		shared = null;

		// titulos parecidos (so entre quem compartilha uma palavra rara)
		Map<String, List<Integer>> tokenIndex = new HashMap<>();
		for (int i = 0; i < n; i++) {
			for (String t : all.get(i).tokens) {
				if (t.length() >= 3) {
					put(tokenIndex, t, i);
				}
			}
		}
		Set<Long> seen = new HashSet<>();
		int tcap = Math.max(25, (int) Math.sqrt(n) * 2);
		for (List<Integer> l : tokenIndex.values()) {
			if (l.size() < 2 || l.size() > tcap) {
				continue;
			}
			for (int a = 0; a < l.size(); a++) {
				for (int b = a + 1; b < l.size(); b++) {
					int i = l.get(a);
					int j = l.get(b);
					long k = pairKey(i, j, n);
					if (!seen.add(k)) {
						continue;
					}
					Game x = all.get(i);
					Game y = all.get(j);
					if (x.normTitle.equals(y.normTitle) || !x.numberTokens.equals(y.numberTokens)) {
						continue; // igual ja contado; numeros diferentes = sequencia
					}
					if (similarTitle(x, y)) {
						pair(pairs, i, j, n).add(25, "título parecido");
					}
				}
			}
		}
		if (progress.isCancelled()) {
			return;
		}

		// autor igual reforça qualquer evidencia ja existente
		for (Map.Entry<Long, Pair> e : pairs.entrySet()) {
			int i = (int) (e.getKey() / n);
			int j = (int) (e.getKey() % n);
			String a = all.get(i).author.trim();
			if (!a.isEmpty() && a.equalsIgnoreCase(all.get(j).author.trim())) {
				e.getValue().add(10, "mesmo autor");
			}
		}

		// ---- grupos certos: uniao so com evidencias fortes
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			parent[i] = i;
		}
		List<Long> strong = new ArrayList<>();
		List<Long> weak = new ArrayList<>();
		for (Map.Entry<Long, Pair> e : pairs.entrySet()) {
			int s = e.getValue().score;
			if (s >= opt.strongThreshold) {
				strong.add(e.getKey());
				int i = (int) (e.getKey() / n);
				int j = (int) (e.getKey() % n);
				parent[find(parent, i)] = find(parent, j);
			} else if (s >= opt.weakThreshold) {
				weak.add(e.getKey());
			}
		}
		Map<Integer, List<Integer>> clusters = new HashMap<>();
		for (int i = 0; i < n; i++) {
			List<Integer> l = clusters.get(find(parent, i));
			if (l == null) {
				l = new ArrayList<>();
				clusters.put(find(parent, i), l);
			}
			l.add(i);
		}
		// melhor aresta por cluster (nivel e motivo)
		Map<Integer, Pair> clusterBest = new HashMap<>();
		for (long k : strong) {
			int r = find(parent, (int) (k / n));
			Pair cur = clusterBest.get(r);
			Pair p = pairs.get(k);
			if (cur == null || p.score > cur.score) {
				clusterBest.put(r, p);
			}
		}
		Map<Integer, Game> keeperOf = new HashMap<>();
		for (Map.Entry<Integer, List<Integer>> e : clusters.entrySet()) {
			List<Game> members = new ArrayList<>();
			for (int i : e.getValue()) {
				members.add(all.get(i));
			}
			Game keep = pickKeeper(members);
			keeperOf.put(e.getKey(), keep);
			if (members.size() < 2) {
				continue;
			}
			Pair best = clusterBest.get(e.getKey());
			List<Game> remove = new ArrayList<>(members);
			remove.remove(keep);
			Level lvl = best == null ? Level.SAME_GAME
					: best.best >= 100 ? Level.IDENTICAL
					: best.best >= 70 ? Level.SAME_CODE : Level.SAME_GAME;
			res.groups.add(new Group(lvl, keep, remove, best == null ? "" : joinReasons(best),
					best == null ? 0 : best.score));
		}

		// ---- suspeitas: arestas medias entre clusters diferentes
		int[] cp = new int[n];
		for (int i = 0; i < n; i++) {
			cp[i] = i;
		}
		Map<Integer, Pair> weakBest = new HashMap<>();
		List<long[]> weakEdges = new ArrayList<>();
		for (long k : weak) {
			int i = (int) (k / n);
			int j = (int) (k % n);
			int ri = find(parent, i);
			int rj = find(parent, j);
			if (ri == rj) {
				continue;
			}
			cp[find(cp, ri)] = find(cp, rj);
			weakEdges.add(new long[]{ri, rj, k});
		}
		for (long[] we : weakEdges) {
			int r = find(cp, (int) we[0]);
			Pair p = pairs.get(we[2]);
			Pair cur = weakBest.get(r);
			if (cur == null || p.score > cur.score) {
				weakBest.put(r, p);
			}
		}
		Map<Integer, Set<Integer>> weakGroups = new HashMap<>();
		for (long[] we : weakEdges) {
			int r = find(cp, (int) we[0]);
			Set<Integer> s = weakGroups.get(r);
			if (s == null) {
				s = new LinkedHashSet<>();
				weakGroups.put(r, s);
			}
			s.add((int) we[0]);
			s.add((int) we[1]);
		}
		for (Map.Entry<Integer, Set<Integer>> e : weakGroups.entrySet()) {
			List<Game> keepers = new ArrayList<>();
			for (int root : e.getValue()) {
				keepers.add(keeperOf.get(root));
			}
			Game keep = pickKeeper(keepers);
			List<Game> remove = new ArrayList<>();
			for (int root : e.getValue()) {
				if (keeperOf.get(root) == keep) {
					continue;
				}
				for (int i : clusters.get(root)) { // o cluster todo sai junto
					remove.add(all.get(i));
				}
			}
			Pair p = weakBest.get(e.getKey());
			res.groups.add(new Group(Level.SUSPECT, keep, remove, p == null ? "" : joinReasons(p),
					p == null ? 0 : p.score));
		}

		Collections.sort(res.groups, new Comparator<Group>() {
			@Override
			public int compare(Group a, Group b) {
				int c = a.level.compareTo(b.level);
				return c != 0 ? c : a.keep.title.compareToIgnoreCase(b.keep.title);
			}
		});
	}

	private static String joinReasons(Pair p) {
		LinkedHashSet<String> uniq = new LinkedHashSet<>(p.reasons);
		StringBuilder sb = new StringBuilder();
		for (String r : uniq) {
			if (sb.length() > 0) {
				sb.append("; ");
			}
			sb.append(r);
		}
		return sb.toString();
	}

	/** O que fica: convertido > tem saves > saves mais recentes > tem config > tem icone > menor id. */
	static Game pickKeeper(List<Game> members) {
		Game best = null;
		for (Game g : members) {
			if (best == null || better(g, best)) {
				best = g;
			}
		}
		return best;
	}

	private static boolean better(Game a, Game b) {
		if (a.converted != b.converted) {
			return a.converted;
		}
		boolean as = a.savesSize > 0;
		boolean bs = b.savesSize > 0;
		if (as != bs) {
			return as;
		}
		if (a.lastSave != b.lastSave) {
			return a.lastSave > b.lastSave;
		}
		if (a.hasConfig != b.hasConfig) {
			return a.hasConfig;
		}
		if (a.hasIcon != b.hasIcon) {
			return a.hasIcon;
		}
		return a.id < b.id;
	}

	private static void exact(Map<String, List<Integer>> m, Map<Long, Pair> pairs, int n, int pts,
							  String why) {
		for (List<Integer> l : m.values()) {
			if (l.size() < 2 || l.size() > 200) {
				continue;
			}
			for (int a = 0; a < l.size(); a++) {
				for (int b = a + 1; b < l.size(); b++) {
					pair(pairs, l.get(a), l.get(b), n).add(pts, why);
				}
			}
		}
	}

	private static void put(Map<String, List<Integer>> m, String key, int i) {
		if (key == null || key.isEmpty()) {
			return;
		}
		List<Integer> l = m.get(key);
		if (l == null) {
			l = new ArrayList<>(2);
			m.put(key, l);
		}
		l.add(i);
	}

	private static void addTo(Map<String, List<File>> m, String key, File f) {
		List<File> l = m.get(key);
		if (l == null) {
			l = new ArrayList<>(2);
			m.put(key, l);
		}
		l.add(f);
	}

	private static String pct(double d) {
		return Math.round(d * 100) + "%";
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	// ---- titulos

	private static boolean isNumberToken(String t) {
		return t.matches("\\d+") || ROMAN.matcher(t).matches();
	}

	/** "ii" -> "2", "03" -> "3". */
	private static String numberValue(String t) {
		if (t.matches("\\d+")) {
			return String.valueOf(Long.parseLong(t.length() > 12 ? t.substring(0, 12) : t));
		}
		switch (t) {
			case "i": return "1";
			case "ii": return "2";
			case "iii": return "3";
			case "iv": return "4";
			case "v": return "5";
			case "vi": return "6";
			case "vii": return "7";
			case "viii": return "8";
			case "ix": return "9";
			default: return "10";
		}
	}

	private static boolean similarTitle(Game a, Game b) {
		if (a.tokens.isEmpty() || b.tokens.isEmpty()) {
			return false;
		}
		int inter = 0;
		for (String t : a.tokens) {
			if (b.tokens.contains(t)) {
				inter++;
			}
		}
		int union = a.tokens.size() + b.tokens.size() - inter;
		double jac = inter / (double) union;
		if (jac >= 0.75 || (inter >= 2 && inter == Math.min(a.tokens.size(), b.tokens.size()))) {
			return true;
		}
		String x = a.normTitle.replace(" ", "");
		String y = b.normTitle.replace(" ", "");
		int max = Math.max(x.length(), y.length());
		if (max < 7 || Math.abs(x.length() - y.length()) > 2) {
			return false;
		}
		int d = levenshtein(x, y, 2);
		return d <= (max >= 12 ? 2 : 1);
	}

	/** Distancia de edicao; para cedo se passar de {@code limit}. */
	static int levenshtein(String a, String b, int limit) {
		int n = a.length();
		int m = b.length();
		if (Math.abs(n - m) > limit) {
			return limit + 1;
		}
		int[] prev = new int[m + 1];
		int[] cur = new int[m + 1];
		for (int j = 0; j <= m; j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= n; i++) {
			cur[0] = i;
			int rowMin = cur[0];
			for (int j = 1; j <= m; j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
				rowMin = Math.min(rowMin, cur[j]);
			}
			if (rowMin > limit) {
				return limit + 1;
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[m];
	}

	// ---- fase 3: pastas orfas

	private static void findOrphans(List<Game> all, Options opt, Hasher hasher, Result res,
									Progress progress) {
		progress.onPhase("Procurando pastas sem dono", 0, 1);
		Set<String> names = new HashSet<>();
		Map<String, Game> byHash = new HashMap<>();
		for (Game g : all) {
			names.add(g.dir.getName());
			if (g.contentHash != null) {
				byHash.put(g.contentHash, g);
			}
		}
		if (opt.appsDir != null) {
			File[] dirs = opt.appsDir.listFiles();
			if (dirs != null) {
				for (File d : dirs) {
					if (!d.isDirectory() || names.contains(d.getName()) || d.getName().startsWith(".")) {
						continue;
					}
					Game copyOf = null;
					File jar = new File(d, "res.jar");
					if (jar.isFile()) {
						try {
							copyOf = byHash.get(hasher.contentHash(jar));
						} catch (Exception ignored) {
						}
					}
					res.extras.add(new Extra(ExtraKind.ORPHAN_APP, d, sizeOf(d), copyOf));
				}
			}
		}
		if (opt.configsDir != null) {
			File[] dirs = opt.configsDir.listFiles();
			if (dirs != null) {
				for (File d : dirs) {
					if (d.isDirectory() && !names.contains(d.getName())) {
						res.extras.add(new Extra(ExtraKind.ORPHAN_CONFIG, d, sizeOf(d), null));
					}
				}
			}
		}
	}

	// ---- fase 4: arquivos repetidos de verdade

	private static void findFileDuplicates(Options opt, ExecutorService pool, Result res,
										   Progress progress) throws Exception {
		Map<Long, List<File>> bySize = new HashMap<>();
		int[] count = {0};
		for (File root : opt.fileRoots) {
			walk(root, bySize, count, progress);
			if (progress.isCancelled()) {
				return;
			}
		}
		List<List<File>> candidates = new ArrayList<>();
		for (List<File> l : bySize.values()) {
			if (l.size() > 1) {
				candidates.add(l);
			}
		}
		int total = candidates.size();
		int done = 0;
		for (List<File> sameSize : candidates) {
			if (progress.isCancelled()) {
				return;
			}
			progress.onPhase("Comparando arquivos", ++done, total);
			long size = sameSize.get(0).length();
			// amostra (inicio+fim) antes do hash completo
			Map<String, List<File>> bySample = new HashMap<>();
			for (File f : sameSize) {
				try {
					addTo(bySample, sample(f, size), f);
				} catch (Exception ignored) {
				}
			}
			for (List<File> sameSample : bySample.values()) {
				if (sameSample.size() < 2) {
					continue;
				}
				Map<String, List<File>> byFull = new HashMap<>();
				for (File f : sameSample) {
					try {
						addTo(byFull, sha256(f), f);
					} catch (Exception ignored) {
					}
				}
				for (List<File> g : byFull.values()) {
					if (g.size() < 2) {
						continue;
					}
					Collections.sort(g, new Comparator<File>() {
						@Override
						public int compare(File a, File b) {
							// o que fica: nome mais curto, sem prefixo libretro__, mais antigo
							int pa = a.getName().startsWith("libretro__") ? 1 : 0;
							int pb = b.getName().startsWith("libretro__") ? 1 : 0;
							if (pa != pb) {
								return Integer.compare(pa, pb);
							}
							if (a.getName().length() != b.getName().length()) {
								return Integer.compare(a.getName().length(), b.getName().length());
							}
							return Long.compare(a.lastModified(), b.lastModified());
						}
					});
					// arquivo protegido (shader em uso) tem prioridade para ficar
					int keep = 0;
					for (int i = 0; i < g.size(); i++) {
						if (opt.protectedPaths.contains(g.get(i).getPath())) {
							keep = i;
							break;
						}
					}
					boolean deletable = opt.deletableRoot != null;
					if (deletable) {
						String root = opt.deletableRoot.getAbsolutePath() + File.separator;
						for (File f : g) {
							if (!f.getAbsolutePath().startsWith(root)) {
								deletable = false;
							}
						}
					}
					res.fileGroups.add(new FileGroup(g, size, deletable, keep));
				}
			}
		}
		Collections.sort(res.fileGroups, new Comparator<FileGroup>() {
			@Override
			public int compare(FileGroup a, FileGroup b) {
				return Long.compare(b.wasted(), a.wasted());
			}
		});
	}

	private static void walk(File f, Map<Long, List<File>> bySize, int[] count, Progress progress) {
		if (progress.isCancelled()) {
			return;
		}
		File[] kids = f.listFiles();
		if (kids == null) {
			if (f.isFile() && f.length() > 0) {
				List<File> l = bySize.get(f.length());
				if (l == null) {
					l = new ArrayList<>(2);
					bySize.put(f.length(), l);
				}
				l.add(f);
				if (++count[0] % 200 == 0) {
					progress.onPhase("Listando arquivos", count[0], 0);
				}
			}
			return;
		}
		for (File k : kids) {
			if (!k.getName().startsWith(".")) {
				walk(k, bySize, count, progress);
			}
		}
	}

	private static String sample(File f, long size) throws Exception {
		int len = (int) Math.min(size, 64 * 1024);
		byte[] head = new byte[len];
		byte[] tail = new byte[len];
		try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
			r.readFully(head);
			r.seek(Math.max(0, size - len));
			r.readFully(tail);
		}
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.update(head);
		md.update(tail);
		return hex(md.digest());
	}

	// ------------------------------------------------------------------ utilitarios

	private static long[] sortedUnique(List<Long> l) {
		long[] a = new long[l.size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = l.get(i);
		}
		Arrays.sort(a);
		int w = 0;
		for (int i = 0; i < a.length; i++) {
			if (i == 0 || a[i] != a[i - 1]) {
				a[w++] = a[i];
			}
		}
		return Arrays.copyOf(a, w);
	}

	static int intersect(long[] a, long[] b) {
		int i = 0;
		int j = 0;
		int c = 0;
		while (i < a.length && j < b.length) {
			if (a[i] == b[j]) {
				c++;
				i++;
				j++;
			} else if (a[i] < b[j]) {
				i++;
			} else {
				j++;
			}
		}
		return c;
	}

	public static long sizeOf(File f) {
		if (f == null) {
			return 0;
		}
		File[] kids = f.listFiles();
		if (kids == null) {
			return f.length();
		}
		long s = 0;
		for (File k : kids) {
			s += sizeOf(k);
		}
		return s;
	}

	private static long newest(File f) {
		long t = f.lastModified();
		File[] kids = f.listFiles();
		if (kids != null) {
			for (File k : kids) {
				t = Math.max(t, newest(k));
			}
		}
		return t;
	}

	private static String sha256(File f) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		try (InputStream in = new FileInputStream(f)) {
			byte[] b = new byte[32 * 1024];
			int r;
			while ((r = in.read(b)) > 0) {
				md.update(b, 0, r);
			}
		}
		return hex(md.digest());
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return out.toString();
	}

	// ------------------------------------------------------------------ relatorio

	public static String formatSize(long b) {
		if (b >= 1024L * 1024 * 1024) {
			return String.format(Locale.ROOT, "%.2f GB", b / 1073741824.0);
		}
		if (b >= 1024L * 1024) {
			return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
		}
		return Math.max(1, b / 1024) + " KB";
	}

	public static String describe(Game g) {
		StringBuilder sb = new StringBuilder(g.title);
		if (!g.version.isEmpty()) {
			sb.append(" v").append(g.version);
		}
		sb.append(" [").append(g.dir.getName()).append("] ").append(formatSize(g.dirSize));
		sb.append(g.converted ? ", convertido" : ", NÃO convertido");
		if (g.savesSize > 0) {
			sb.append(", saves ").append(formatSize(g.savesSize));
		}
		if (g.unreadable) {
			sb.append(", jar ilegível");
		}
		return sb.toString();
	}

	/** Relatorio completo em texto (vai para configuracoes/duplicatas-relatorio.txt). */
	public static String report(Result r) {
		StringBuilder sb = new StringBuilder();
		sb.append("RELATÓRIO DE DUPLICATAS\n");
		sb.append("Jogos analisados: ").append(r.gamesScanned)
				.append(" (ilegíveis: ").append(r.gamesUnreadable).append(")\n");
		sb.append("Pares comparados: ").append(r.pairsEvaluated)
				.append(" | tempo: ").append(r.millis / 1000.0).append(" s\n\n");
		int n = 0;
		for (Group g : r.groups) {
			sb.append("== ").append(++n).append(". ").append(g.level.label).append(" (pontos ").append(g.score)
					.append(")\n   motivo: ").append(g.reason).append('\n');
			sb.append("   FICA:  ").append(describe(g.keep)).append('\n');
			for (Game x : g.remove) {
				sb.append("   SAI:   ").append(describe(x)).append('\n');
			}
			sb.append('\n');
		}
		if (!r.extras.isEmpty()) {
			sb.append("PASTAS SEM DONO\n");
			for (Extra e : r.extras) {
				sb.append(" - ").append(e.kind.label).append(": ").append(e.dir.getPath())
						.append(" (").append(formatSize(e.bytes)).append(')');
				if (e.copyOf != null) {
					sb.append(" = cópia de ").append(e.copyOf.title);
				}
				sb.append('\n');
			}
			sb.append('\n');
		}
		if (!r.fileGroups.isEmpty()) {
			sb.append("ARQUIVOS REPETIDOS\n");
			for (FileGroup fg : r.fileGroups) {
				sb.append(" - ").append(formatSize(fg.size)).append(" x ").append(fg.files.size())
						.append(fg.deletable ? " (pode limpar)" : " (só informativo)").append('\n');
				for (int i = 0; i < fg.files.size(); i++) {
					sb.append(i == fg.keepIndex ? "     fica: " : "     igual: ")
							.append(fg.files.get(i).getPath()).append('\n');
				}
			}
		}
		for (String e : r.errors) {
			sb.append("ERRO: ").append(e).append('\n');
		}
		return sb.toString();
	}
}
