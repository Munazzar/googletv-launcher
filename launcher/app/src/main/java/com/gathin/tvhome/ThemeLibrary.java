package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Themes kept on the TV in files/themes/&lt;name&gt;/, each a theme folder as in the repo's themes/ (pushed once with
 * tools/theme-library.ps1). Picking one in Launcher settings copies it over the active theme files, the same files
 * tools/theme.ps1 writes; the new config.json's timestamp then resets the on-TV choices like a push does.
 */
final class ThemeLibrary {

    static final String DIR = "themes";

    private ThemeLibrary() {
    }

    /** Theme folders under {@code filesDir}/themes that have a config.json, by name. */
    static List<File> list(File filesDir) {
        List<File> out = new ArrayList<>();
        File[] dirs = filesDir == null ? null : new File(filesDir, DIR).listFiles();
        if (dirs == null) return out;
        for (File d : dirs) if (new File(d, Config.FILE_NAME).isFile()) out.add(d);
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /** The stored theme whose config.json is the active one byte for byte, or null (built-in, or pushed from a PC). */
    static File active(File filesDir, List<File> themes) {
        try {
            byte[] current = Files.readAllBytes(new File(filesDir, Config.FILE_NAME).toPath());
            for (File t : themes) {
                if (Arrays.equals(current, Files.readAllBytes(new File(t, Config.FILE_NAME).toPath()))) return t;
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }

    /** Replaces the active theme files with {@code theme}'s; null leaves none, which is the built-in look. */
    static void apply(File filesDir, File theme) throws IOException {
        delete(new File(filesDir, Config.FILE_NAME));
        delete(new File(filesDir, Config.WALLPAPER_NAME));
        delete(new File(filesDir, "verses.json"));
        delete(new File(filesDir, "wallpapers"));
        if (theme != null) copy(theme, filesDir);
    }

    private static void copy(File from, File to) throws IOException {
        File[] files = from.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().equals("screenshot.jpg")) continue;   // only for people browsing the repo
            File target = new File(to, f.getName());
            if (f.isDirectory()) {
                if (!target.isDirectory() && !target.mkdirs()) throw new IOException("mkdir " + target);
                copy(f, target);
            } else {
                Files.copy(f.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : Arrays.asList(children)) delete(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
