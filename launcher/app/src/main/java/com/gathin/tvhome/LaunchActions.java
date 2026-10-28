package com.gathin.tvhome;

import android.content.Intent;
import android.net.Uri;
import java.util.Map;
import java.util.function.BiFunction;

/** Selects a configured, package-scoped action only when it resolves; otherwise keeps the normal launch. */
final class LaunchActions {
    /** A launch_actions value "view:<uri>" opens that URI in the package instead of sending an action. */
    static final String VIEW_PREFIX = "view:";

    private LaunchActions() {}

    static <T> T select(String pkg, Map<String, String> actions, T fallback,
                        BiFunction<String, String, T> resolver) {
        String action = actions.get(pkg);
        if (action == null) return fallback;
        T custom = resolver.apply(pkg, action);
        return custom != null ? custom : fallback;
    }

    private static final String INPUT_PREFIX = "input:";

    /** The TV input id from an "input:<id>" launch_actions value, or null when the value isn't one, including
     *  an empty id ("input:" alone), which falls back to a normal launch rather than an empty input switch. */
    static String inputId(String action) {
        if (action == null || !action.startsWith(INPUT_PREFIX)) return null;
        String id = action.substring(INPUT_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }

    /** The URI of a "view:<uri>" value, or null for a plain action. */
    static String viewUri(String value) {
        if (value == null || !value.startsWith(VIEW_PREFIX)) return null;
        String uri = value.substring(VIEW_PREFIX.length()).trim();
        return uri.isEmpty() ? null : uri;
    }

    /** The intent for a launch_actions value, limited to its package: ACTION_VIEW on the URI, an activity of the
     *  package for a value starting with "." (".ui.screensaver.TestActivity", or ".MainActivity?tab=dock" with
     *  string extras, sent as ACTION_VIEW like an app shortcut), or the action. */
    static Intent intent(String pkg, String value) {
        if (value.startsWith(".")) {
            String[] parts = value.trim().split("\\?", 2);
            Intent i = new Intent(Intent.ACTION_VIEW).setClassName(pkg, pkg + parts[0]);
            if (parts.length > 1) for (String pair : parts[1].split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) i.putExtra(pair.substring(0, eq), pair.substring(eq + 1));
            }
            return i;
        }
        String uri = viewUri(value);
        Intent i = uri != null ? new Intent(Intent.ACTION_VIEW, Uri.parse(uri)) : new Intent(value);
        return i.setPackage(pkg);
    }
}
