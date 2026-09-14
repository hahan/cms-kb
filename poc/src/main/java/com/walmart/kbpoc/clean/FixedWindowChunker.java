package com.walmart.kbpoc.clean;

import java.util.ArrayList;
import java.util.List;

/**
 * The BASELINE chunking strategy: fixed-size word windows with light overlap,
 * no structural awareness at all. This is deliberately the naive approach
 * being compared against — not a strawman, just genuinely simple.
 */
public final class FixedWindowChunker {

    private static final int WINDOW_WORDS = 80;
    private static final int OVERLAP_WORDS = 15;

    public static List<String> chunk(String text) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < words.length) {
            int end = Math.min(i + WINDOW_WORDS, words.length);
            chunks.add(String.join(" ", java.util.Arrays.asList(words).subList(i, end)));
            if (end == words.length) {
                break;
            }
            i += (WINDOW_WORDS - OVERLAP_WORDS);
        }
        return chunks;
    }
}
