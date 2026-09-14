package com.walmart.kbpoc.graph;

import com.walmart.kbpoc.model.KbRelation;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory relationship graph (see docs/poc-scope.md: a real graph DB is
 * out of scope for this POC -- a few dozen edges is enough to demonstrate
 * multi-hop queries that vector search structurally cannot answer).
 */
public class KbGraph {

    public static class Edge {
        public final String predicate;
        public final String source;
        public final double confidence;

        Edge(String predicate, String source, double confidence) {
            this.predicate = predicate;
            this.source = source;
            this.confidence = confidence;
        }
    }

    private final Graph<String, Edge> graph = new DefaultDirectedGraph<>(null, null, false);

    public static KbGraph build(List<KbRelation> relations) {
        KbGraph g = new KbGraph();
        for (KbRelation r : relations) {
            g.graph.addVertex(r.subject);
            g.graph.addVertex(r.object);
            g.graph.addEdge(r.subject, r.object, new Edge(r.predicate, r.source, r.confidence));
        }
        return g;
    }

    /** Docs that reference (or otherwise point to) the given doc: incoming edges of any predicate. */
    public List<String> incomingReferences(String docId, String predicate) {
        if (!graph.containsVertex(docId)) {
            return List.of();
        }
        return graph.incomingEdgesOf(docId).stream()
                .filter(e -> e.predicate.equals(predicate))
                .map(e -> graph.getEdgeSource(e))
                .collect(Collectors.toList());
    }

    public List<String> outgoing(String docId, String predicate) {
        if (!graph.containsVertex(docId)) {
            return List.of();
        }
        return graph.outgoingEdgesOf(docId).stream()
                .filter(e -> e.predicate.equals(predicate))
                .map(e -> graph.getEdgeTarget(e))
                .collect(Collectors.toList());
    }

    /** Both directions -- CONTRADICTS is authored as A->B but is symmetric in meaning. */
    public List<String> contradictionsOf(String docId) {
        List<String> results = new ArrayList<>();
        results.addAll(outgoing(docId, "CONTRADICTS"));
        results.addAll(incomingReferences(docId, "CONTRADICTS"));
        return results;
    }

    public List<String> whatReferences(String docId) {
        return incomingReferences(docId, "REFERENCES");
    }

    public String supersededBy(String docId) {
        List<String> next = outgoing(docId, "SUPERSEDES"); // docId SUPERSEDES x means docId is newer than x
        // we want: is docId itself superseded by something newer?
        List<String> newer = incomingReferences(docId, "SUPERSEDES"); // y SUPERSEDES docId -> y is newer
        return newer.isEmpty() ? null : newer.get(0);
    }
}
