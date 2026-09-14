package com.walmart.kbpoc.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The category-aware filter identified as missing by the Q2 finding
 * (docs/learnings.md): heading-breadcrumb embeddings alone don't reliably
 * separate near-duplicate policies (e.g. "Electronics" vs "Electronics >
 * Accessories"). This is a deterministic, keyword-based query router --
 * no extra LLM call at query time, consistent with the rule-based
 * block_source path already used elsewhere in this POC.
 *
 * Rule: for each "distinguishing" sub-category present in the corpus, if the
 * query mentions that sub-category's keywords, RESULTS MUST be tagged with
 * it; if the query does NOT mention them, results tagged with it are
 * EXCLUDED. This directly fixes the Q2 failure mode: a general electronics
 * query no longer loses to an "Electronics > Accessories" near-duplicate
 * just because that block happened to score higher on raw similarity.
 */
public final class CategoryRouter {

    private static final Map<String, List<String>> DISTINGUISHING_TAG_KEYWORDS = new LinkedHashMap<>();

    static {
        DISTINGUISHING_TAG_KEYWORDS.put("Accessories",
                List.of("accessor", "cable", "charger", "headphone", "case"));
        DISTINGUISHING_TAG_KEYWORDS.put("Marketplace",
                List.of("marketplace", "third-party seller", "third party seller", "seller"));
        DISTINGUISHING_TAG_KEYWORDS.put("General Merchandise",
                List.of("general merchandise", "toys", "clothing", "home goods", "sporting goods"));
        DISTINGUISHING_TAG_KEYWORDS.put("Membership",
                List.of("membership", "walmart+", "walmart plus"));
        DISTINGUISHING_TAG_KEYWORDS.put("Price Match",
                List.of("price match", "competitor price", "price matching"));
        DISTINGUISHING_TAG_KEYWORDS.put("Delivery",
                List.of("shipping", "delivery", "deliver"));
    }

    private CategoryRouter() {
    }

    /** True if this hit's category should survive the filter for this query. */
    public static boolean isEligible(String queryText, String category) {
        String q = queryText.toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category;

        for (Map.Entry<String, List<String>> e : DISTINGUISHING_TAG_KEYWORDS.entrySet()) {
            String tag = e.getKey();
            boolean queryMentionsTag = e.getValue().stream().anyMatch(q::contains);
            boolean resultTaggedWith = cat.contains(tag);

            if (queryMentionsTag && !resultTaggedWith) {
                return false; // query specifically asked about this sub-category; this result isn't it
            }
            if (!queryMentionsTag && resultTaggedWith) {
                return false; // query did NOT ask about this sub-category; exclude the near-duplicate
            }
        }
        return true;
    }
}
