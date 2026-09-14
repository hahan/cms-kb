package com.walmart.kbpoc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmart.kbpoc.model.KbDocument;
import com.walmart.kbpoc.model.KbRelation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the synthetic corpus (HTML files + metadata.json + relations.json) from classpath resources. */
public final class Corpus {

    private static final String[] DOC_IDS = {
            "ret-elec-v2", "ret-elec-v1", "ret-marketplace", "ret-general",
            "sop-elec-returns", "faq-returns-elec", "policy-window-conflict-a",
            "policy-window-conflict-b", "faq-price-match", "faq-delivery",
            "policy-membership-returns"
    };

    private final ObjectMapper mapper = new ObjectMapper();

    public record DocEnvelope(
            String docId, String docType, String title, String sourceUrl,
            List<String> category, List<String> region, KbDocument.Governance governance) {
    }

    public List<String> docIds() {
        return List.of(DOC_IDS);
    }

    public String rawHtml(String docId) {
        try (InputStream in = getClass().getResourceAsStream("/corpus/html/" + docId + ".html")) {
            if (in == null) {
                throw new IllegalArgumentException("No corpus HTML for docId=" + docId);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, DocEnvelope> loadMetadata() {
        try (InputStream in = getClass().getResourceAsStream("/corpus/metadata.json")) {
            List<Map<String, Object>> raw = mapper.readValue(in, List.class);
            Map<String, DocEnvelope> byId = new HashMap<>();
            for (Map<String, Object> m : raw) {
                Map<String, Object> gov = (Map<String, Object>) m.get("governance");
                KbDocument.Governance g = new KbDocument.Governance();
                g.owner = (String) gov.get("owner");
                g.approvalStatus = (String) gov.get("approvalStatus");
                g.effectiveDate = (String) gov.get("effectiveDate");
                g.expirationDate = (String) gov.get("expirationDate");
                g.version = ((Number) gov.get("version")).intValue();
                g.supersedes = (String) gov.get("supersedes");
                g.supersededBy = (String) gov.get("supersededBy");
                g.sensitivity = (String) gov.get("sensitivity");

                DocEnvelope env = new DocEnvelope(
                        (String) m.get("docId"),
                        (String) m.get("docType"),
                        (String) m.get("title"),
                        (String) m.get("sourceUrl"),
                        (List<String>) m.get("category"),
                        (List<String>) m.get("region"),
                        g);
                byId.put(env.docId(), env);
            }
            return byId;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<KbRelation> loadRelations() {
        try (InputStream in = getClass().getResourceAsStream("/corpus/relations.json")) {
            List<Map<String, Object>> raw = mapper.readValue(in, List.class);
            List<KbRelation> relations = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                KbRelation r = new KbRelation();
                r.subject = (String) m.get("subject");
                r.predicate = (String) m.get("predicate");
                r.object = (String) m.get("object");
                r.source = (String) m.get("source");
                r.confidence = ((Number) m.get("confidence")).doubleValue();
                relations.add(r);
            }
            return relations;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
