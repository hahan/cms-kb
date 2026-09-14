package com.walmart.kbpoc.model;

import java.util.ArrayList;
import java.util.List;

public class KbBlock {
    public String blockId;
    public String blockType;      // eligibility_criteria | procedure_steps | step | exception | escalation | qa_pair | disclaimer | intro
    public int order;
    public String parentBlockId;  // nullable
    public List<String> headingPath = new ArrayList<>();
    public String text;           // clean text -> embedded
    public String html;           // sanitized fragment -> display only, never embedded
    public String blockSource;    // html_heuristic | pdf_layout | plaintext_pattern
}
