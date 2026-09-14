package com.walmart.kbpoc.llm;

import java.util.ArrayList;
import java.util.List;

/** Raw shape returned by the LLM segmenter, before being folded into a KbBlock. */
public class LlmBlock {
    public String blockType;
    public int order;
    public String heading;      // single heading text for this section, e.g. "Eligibility"
    public String text;
    public List<LlmEntity> entities = new ArrayList<>();

    public static class LlmEntity {
        public String entityType; // dollar_threshold | time_window | required_document
        public String value;
        public String unit;
        public double confidence;
    }
}
