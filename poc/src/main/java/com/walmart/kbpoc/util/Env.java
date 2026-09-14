package com.walmart.kbpoc.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny .env loader. Looks for a KEY=value file at ./poc/.env (relative to
 * wherever the process is run from) and falls back to real environment
 * variables. Never logs values.
 */
public final class Env {

    private static final Map<String, String> DOT_ENV = load();

    private Env() {
    }

    public static String get(String key) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        return DOT_ENV.get(key);
    }

    public static String require(String key) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                    "Missing required config: " + key + ". Set it in poc/.env or as an environment variable.");
        }
        return v;
    }

    private static Map<String, String> load() {
        Map<String, String> map = new HashMap<>();
        for (String candidate : new String[]{".env", "poc/.env"}) {
            Path p = Path.of(candidate);
            if (Files.isRegularFile(p)) {
                try {
                    for (String line : Files.readAllLines(p)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        int idx = trimmed.indexOf('=');
                        if (idx <= 0) {
                            continue;
                        }
                        map.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
                    }
                    return map;
                } catch (IOException e) {
                    // fall through to env-only
                }
            }
        }
        return map;
    }
}
