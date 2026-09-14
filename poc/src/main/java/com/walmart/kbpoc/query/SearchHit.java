package com.walmart.kbpoc.query;

public record SearchHit(String docId, String blockOrChunkInfo, String textSnippet, float score) {
}
