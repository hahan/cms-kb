package com.walmart.kbpoc.query;

import java.util.Set;

public class QuerySpec {
    public enum Kind { VECTOR, GRAPH_REFERENCES, GRAPH_CONTRADICTS }

    public final String id;
    public final String text;
    public final Kind kind;
    public final String trap;
    public final Set<String> expectedDocIds;   // VECTOR: acceptable top-hit docIds
    public final Set<String> forbiddenDocIds;  // VECTOR: must NOT be the top hit
    public final String graphSubjectDocId;     // GRAPH_*: the doc the question is about
    public final Set<String> expectedRelated;  // GRAPH_*: docIds that should come back

    private QuerySpec(String id, String text, Kind kind, String trap, Set<String> expectedDocIds,
                       Set<String> forbiddenDocIds, String graphSubjectDocId, Set<String> expectedRelated) {
        this.id = id;
        this.text = text;
        this.kind = kind;
        this.trap = trap;
        this.expectedDocIds = expectedDocIds;
        this.forbiddenDocIds = forbiddenDocIds;
        this.graphSubjectDocId = graphSubjectDocId;
        this.expectedRelated = expectedRelated;
    }

    public static QuerySpec vector(String id, String text, String trap, Set<String> expected, Set<String> forbidden) {
        return new QuerySpec(id, text, Kind.VECTOR, trap, expected, forbidden, null, null);
    }

    public static QuerySpec graphReferences(String id, String text, String trap, String subjectDocId, Set<String> expectedRelated) {
        return new QuerySpec(id, text, Kind.GRAPH_REFERENCES, trap, null, null, subjectDocId, expectedRelated);
    }

    public static QuerySpec graphContradicts(String id, String text, String trap, String subjectDocId, Set<String> expectedRelated) {
        return new QuerySpec(id, text, Kind.GRAPH_CONTRADICTS, trap, null, null, subjectDocId, expectedRelated);
    }
}
