package ru.woesss.j2me.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;

/** Detects an already-installed JAR by content and normalized game title. */
final class GameFingerprint {
    static final class Match {
        final AppItem app;
        final boolean identical;

        Match(AppItem app, boolean identical) {
            this.app = app;
            this.identical = identical;
        }
    }

    private GameFingerprint() {}

    static Match findMatch(File incomingJar, String incomingTitle, AppRepository repository) throws Exception {
        List<AppItem> installed = repository.getAll().blockingFirst();
        String incomingHash = incomingJar != null && incomingJar.isFile() ? sha256(incomingJar) : null;
        String normalizedTitle = normalizeTitle(incomingTitle);
        AppItem titleMatch = null;

        for (AppItem item : installed) {
            File installedJar = new File(item.getPathExt(), Config.MIDLET_RES_FILE);
            if (incomingHash != null && installedJar.isFile()) {
                try {
                    if (incomingHash.equals(sha256(installedJar))) {
                        return new Match(item, true);
                    }
                } catch (Exception ignored) {
                    // A damaged/unreadable installed archive must not block installation.
                }
            }
            if (titleMatch == null && !normalizedTitle.isEmpty()
                    && normalizedTitle.equals(normalizeTitle(item.getTitle()))) {
                titleMatch = item;
            }
        }
        return titleMatch == null ? null : new Match(titleMatch, false);
    }

    /** Normalize spelling, punctuation, common resolution labels and uploader suffixes. */
    static String normalizeTitle(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\bby\\s+.*$", " ")
                .replaceAll("\\b(240x320|320x240|360x640|480x800|176x220|128x160|hd|sd|mod|remake|edition|versao|version)\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim().replaceAll("\\s+", " ");
        // Common Portuguese/English spelling variants of the same franchise.
        s = s.replaceAll("\\bhomem aranha\\b", "spider man")
             .replaceAll("\\bspiderman\\b", "spider man");
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream raw = new FileInputStream(file); ZipInputStream zip = new ZipInputStream(raw)) {
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || "META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
                    zip.closeEntry();
                    continue;
                }
                digest.update(entry.getName().getBytes("UTF-8"));
                int read;
                while ((read = zip.read(buffer)) != -1) digest.update(buffer, 0, read);
                zip.closeEntry();
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
