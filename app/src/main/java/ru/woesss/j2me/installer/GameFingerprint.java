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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;

/**
 * Detects an installed JAR by content and normalized game title.
 *
 * <p>Os hashes dos jogos ja instalados ficam em cache (memoria + arquivo), entao cada conversao nova
 * so le o jar que esta entrando, em vez de reler e descomprimir a biblioteca inteira.
 */
final class GameFingerprint {
    static final class Match {
        final AppItem app;
        final boolean identical;

        Match(AppItem app, boolean identical) {
            this.app = app;
            this.identical = identical;
        }
    }

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern BY = Pattern.compile("\\bby\\s+.*$");
    private static final Pattern SIZES = Pattern.compile(
            "\\b(240x320|320x240|360x640|480x800|176x220|128x160|hd|sd|mod|remake|edition|versao|version)\\b");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern HOMEM_ARANHA = Pattern.compile("\\bhomem aranha\\b");
    private static final Pattern SPIDERMAN = Pattern.compile("\\bspiderman\\b");

    /** "caminho|tamanho|data" -> sha256 do conteudo. */
    private static final Map<String, String> HASHES = new ConcurrentHashMap<>();
    /** titulo original -> titulo normalizado. */
    private static final Map<String, String> TITLES = new ConcurrentHashMap<>();

    private static File store;
    private static boolean loaded;

    private GameFingerprint() {}

    /** Define onde o cache fica salvo (pasta de cache do app). Pode ser chamado varias vezes. */
    static synchronized void init(File cacheDir) {
        if (store == null && cacheDir != null) {
            store = new File(cacheDir, "apex_fingerprints.tsv");
        }
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (store == null || !store.isFile()) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(store))) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.lastIndexOf('\t');
                if (tab > 0) {
                    HASHES.put(line.substring(0, tab), line.substring(tab + 1));
                }
            }
        } catch (Exception ignored) {
            // cache corrompido: recalcula
            HASHES.clear();
        }
    }

    /** Salva no arquivo so os jogos instalados que ainda existem. */
    static synchronized void flush() {
        if (store == null) {
            return;
        }
        String root = new File(Config.getAppDir()).getAbsolutePath() + File.separator;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(store))) {
            for (Map.Entry<String, String> e : HASHES.entrySet()) {
                String key = e.getKey();
                if (key.startsWith(root)) {
                    w.write(key);
                    w.write('\t');
                    w.write(e.getValue());
                    w.write('\n');
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String key(File f) {
        return f.getAbsolutePath() + '|' + f.length() + '|' + f.lastModified();
    }

    /** Hash do jar (do cache se ja foi calculado). */
    static String hashOf(File file) throws Exception {
        ensureLoaded();
        String k = key(file);
        String h = HASHES.get(k);
        if (h == null) {
            h = sha256(file);
            HASHES.put(k, h);
        }
        return h;
    }

    /** O jar recem-instalado e igual ao de origem: reaproveita o hash ja calculado. */
    static void seed(File installedJar, File sourceJar) {
        try {
            String h = HASHES.get(key(sourceJar));
            if (h != null && installedJar.isFile()) {
                HASHES.put(key(installedJar), h);
            }
        } catch (Exception ignored) {
        }
    }

    /** Calcula em paralelo os hashes que faltam dos jogos ja instalados, antes de converter a pasta. */
    static void prewarm(AppRepository repository, ExecutorService pool) {
        try {
            ensureLoaded();
            List<AppItem> installed = repository.getAll().blockingFirst();
            List<Callable<Void>> jobs = new ArrayList<>();
            for (AppItem item : installed) {
                final File jar = new File(item.getPathExt(), Config.MIDLET_RES_FILE);
                if (jar.isFile() && !HASHES.containsKey(key(jar))) {
                    jobs.add(() -> {
                        try {
                            hashOf(jar);
                        } catch (Exception ignored) {
                        }
                        return null;
                    });
                }
            }
            if (!jobs.isEmpty()) {
                pool.invokeAll(jobs);
            }
        } catch (Exception ignored) {
        }
    }

    static Match findMatch(File incomingJar, String incomingTitle, AppRepository repository) throws Exception {
        List<AppItem> installed = repository.getAll().blockingFirst();
        String incomingHash = incomingJar != null && incomingJar.isFile() ? hashOf(incomingJar) : null;
        String normalizedTitle = normalizeTitle(incomingTitle);
        AppItem titleMatch = null;

        for (AppItem item : installed) {
            File installedJar = new File(item.getPathExt(), Config.MIDLET_RES_FILE);
            if (incomingHash != null && installedJar.isFile()) {
                try {
                    if (incomingHash.equals(hashOf(installedJar))) {
                        return new Match(item, true);
                    }
                } catch (Exception ignored) {
                    // An unreadable installed archive must not block installation.
                }
            }
            if (titleMatch == null && !normalizedTitle.isEmpty()
                    && normalizedTitle.equals(normalizeTitle(item.getTitle()))) {
                titleMatch = item;
            }
        }
        return titleMatch == null ? null : new Match(titleMatch, false);
    }

    static String normalizeTitle(String value) {
        if (value == null) return "";
        String cached = TITLES.get(value);
        if (cached != null) return cached;
        String s = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        s = MARKS.matcher(s).replaceAll("");
        s = BY.matcher(s).replaceAll(" ");
        s = SIZES.matcher(s).replaceAll(" ");
        s = NON_ALNUM.matcher(s).replaceAll(" ").trim();
        s = SPACES.matcher(s).replaceAll(" ");
        s = HOMEM_ARANHA.matcher(s).replaceAll("spider man");
        s = SPIDERMAN.matcher(s).replaceAll("spider man");
        s = SPACES.matcher(s.trim()).replaceAll(" ");
        TITLES.put(value, s);
        return s;
    }

    /** Hashes sorted entry names and uncompressed bytes; ZIP order and metadata do not matter. */
    private static String sha256(File file) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        try (InputStream raw = new FileInputStream(file); ZipInputStream zip = new ZipInputStream(raw)) {
            byte[] buffer = new byte[16384];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && !"META-INF/MANIFEST.MF".equalsIgnoreCase(name)) {
                    MessageDigest entryDigest = MessageDigest.getInstance("SHA-256");
                    int read;
                    while ((read = zip.read(buffer)) != -1) entryDigest.update(buffer, 0, read);
                    entries.put(name, entryDigest.digest());
                }
                zip.closeEntry();
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            digest.update(entry.getKey().getBytes("UTF-8"));
            digest.update(entry.getValue());
        }
        byte[] bytes = digest.digest();
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }
}
