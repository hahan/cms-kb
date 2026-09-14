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

    /**
     * ENHANCED: vector similarity over block-level, breadcrumb-prefixed text,
     * with a hard scalar filter always applied: exclude anything that has
     * been superseded (superseded_by != ""). This is the freshness guarantee
     * baseline structurally cannot offer.
     */
    public static List<SearchHit> searchEnhanced(MilvusClient client, float[] queryVector, int topK) {
        String filter = "superseded_by == \"\"";
        SearchResultsWrapper wrapper = MilvusSupport.search(client, MilvusSupport.ENHANCED_COLLECTION,
                queryVector, topK, filter, List.of("doc_id", "block_id", "block_type", "text", "html"));
        List<SearchHit> hits = new ArrayList<>();
        for (SearchResultsWrapper.IDScore r : wrapper.getIDScore(0)) {
            String docId = (String) r.get("doc_id");
            String blockId = (String) r.get("block_id");
            String blockType = (String) r.get("block_type");
            String text = (String) r.get("text");
            hits.add(new SearchHit(docId, blockId + " [" + blockType + "]", text, r.getScore()));
        }
        return hits;
    }
}
