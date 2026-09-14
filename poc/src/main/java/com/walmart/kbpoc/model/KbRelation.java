package com.walmart.kbpoc.model;

public class KbRelation {
    public String subject;   // docId or blockId; defaults to owning document if null
    public String predicate; // SUPERSEDES | PART_OF | EXCEPTION_TO | ESCALATES_TO | REFERENCES | CONTRADICTS
    public String object;    // target docId/blockId
    public String source;    // cms_structural | component_derived | llm_inferred
    public double confidence;
}
