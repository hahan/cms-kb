package com.walmart.kbpoc.model;

import java.util.ArrayList;
import java.util.List;

/** Simplified Java mirror of schemas/kb-document.schema.json, scoped to what the POC needs. */
public class KbDocument {
    public String docId;
    public String docType;      // policy | faq | sop
    public String title;
    public String sourceUrl;
    public String sourceFormat; // html | pdf | txt
    public List<String> category = new ArrayList<>();
    public List<String> region = new ArrayList<>();

    public Governance governance = new Governance();
    public List<KbBlock> blocks = new ArrayList<>();
    public List<KbEntity> entities = new ArrayList<>();
    public List<KbRelation> relations = new ArrayList<>();

    public static class Governance {
        public String owner;
        public String approvalStatus;
        public String effectiveDate;   // ISO date
        public String expirationDate;  // ISO date, nullable
        public int version;
        public String supersedes;      // docId, nullable
        public String supersededBy;    // docId, nullable
        public String sensitivity;     // public | internal | restricted
    }
}
