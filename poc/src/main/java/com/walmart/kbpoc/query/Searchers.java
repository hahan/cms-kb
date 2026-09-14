package com.walmart.kbpoc.query;

import com.walmart.kbpoc.milvus.MilvusSupport;
import io.milvus.client.MilvusClient;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.List;

public final class Searchers {

    private Searchers() {
    }

    /** BASELINE: plain vector similarity, no filter, no structure. */
    public static List<SearchHit> searchBaseline(MilvusClient client, float[] queryVector, int topK) {
        SearchResultsWrapper wrapper = MilvusSupport.search(client, MilvusSupport.BASELINE_COLLECTION,
                queryVector, topK, null, List.of("doc_id", "title", "text"));
        List<SearchHit> hits = new ArrayList<>();
        for (SearchResultsWrapper.IDScore r : wrapper.getIDScore(0)) {
            String docId = (String) r.get("doc_id");
            String title = (String) r.get("title");
            String text = (String) r.get("text");
            hits.add(new SearchHit(docId, title, text, r.getScore()));
        }
        return hits;
    }

    /** How many candidates to over-fetch from Milvus before applying the category filter in Java. */
    private static final int OVER_FETCH = 20;

    /**
     * ENHANCED: vector similarity over block-level, breadcrumb-prefixed text,
     * with two things baseline structurally cannot offer:
     *  1. A hard freshness filter (superseded_by == "") applied in Milvus.
     *  2. A category-aware filter (CategoryRouter, see docs/learnings.md)
     *     applied in Java over an over-fetched candidate set -- this is the
     *     fix for the Q2 finding: a near-duplicate sub-category (e.g.
     *     "Electronics > Accessories") no longer wins on raw similarity
     *     against a general "Electronics" query just because it happened to
     *     score higher.
     */
    public static List<SearchHit> searchEnhanced(MilvusClient client, String queryText, float[] queryVector, int topK) {
        String filter = "superseded_by == \"\"";
        SearchResultsWrapper wrapper = MilvusSupport.search(client, MilvusSupport.ENHANCED_COLLECTION,
                queryVector, OVER_FETCH, filter,
                List.of("doc_id", "block_id", "block_type", "category", "text", "html"));

        List<SearchHit> candidates = new ArrayList<>();
        List<SearchHit> eligible = new ArrayList<>();
        for (SearchResultsWrapper.IDScore r : wrapper.getIDScore(0)) {
            String docId = (String) r.get("doc_id");
            String blockId = (String) r.get("block_id");
            String blockType = (String) r.get("block_type");
            String category = (String) r.get("category");
            String text = (String) r.get("text");
            SearchHit hit = new SearchHit(docId, blockId + " [" + blockType + "]", text, r.getScore());
            candidates.add(hit);
            if (CategoryRouter.isEligible(queryText, category)) {
                eligible.add(hit);
            }
        }

        // Defensive fallback: never return zero results because the router
        // over-excluded -- fall back to the unfiltered ranking in that case.
        List<SearchHit> source = eligible.isEmpty() ? candidates : eligible;
        return source.subList(0, Math.min(topK, source.size()));
    }
}
