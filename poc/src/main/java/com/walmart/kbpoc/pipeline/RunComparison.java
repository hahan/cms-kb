package com.walmart.kbpoc.pipeline;

import com.walmart.kbpoc.embed.EmbeddingService;
import com.walmart.kbpoc.graph.KbGraph;
import com.walmart.kbpoc.milvus.MilvusSupport;
import com.walmart.kbpoc.query.QuerySet;
import com.walmart.kbpoc.query.QuerySpec;
import com.walmart.kbpoc.query.SearchHit;
import com.walmart.kbpoc.query.Searchers;
import com.walmart.kbpoc.util.Corpus;
import io.milvus.client.MilvusServiceClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs the fixed query set (docs/poc-scope.md) against both pipelines and
 * produces a single baseline-vs-enhanced comparison table.
 */
public class RunComparison {

    public static void main(String[] args) throws IOException {
        Corpus corpus = new Corpus();
        KbGraph graph = KbGraph.build(corpus.loadRelations());
        MilvusServiceClient client = MilvusSupport.connect();

        StringBuilder md = new StringBuilder();
        md.append("# POC Comparison: Baseline vs Enhanced\n\n");
        md.append("| # | Query | Trap | Baseline result | Baseline | Enhanced result | Enhanced |\n");
        md.append("|---|---|---|---|---|---|---|\n");

        int basslinePass = 0, enhancedPass = 0;
        int total = 0;

        try (EmbeddingService embedder = new EmbeddingService()) {
            for (QuerySpec q : QuerySet.all()) {
                total++;
                System.out.println("\n=== " + q.id + ": " + q.text + " ===");

                if (q.kind == QuerySpec.Kind.VECTOR) {
                    float[] vec = embedder.embed(q.text);
                    List<SearchHit> baseHits = Searchers.searchBaseline(client, vec, 3);
                    List<SearchHit> enhHits = Searchers.searchEnhanced(client, q.text, vec, 3);

                    SearchHit baseTop = baseHits.isEmpty() ? null : baseHits.get(0);
                    SearchHit enhTop = enhHits.isEmpty() ? null : enhHits.get(0);

                    boolean basePass = passesVector(baseTop, q);
                    boolean enhPass = passesVector(enhTop, q);
                    if (basePass) basslinePass++;
                    if (enhPass) enhancedPass++;

                    System.out.println("baseline top: " + describe(baseTop) + " -> " + verdict(basePass));
                    System.out.println("enhanced top: " + describe(enhTop) + " -> " + verdict(enhPass));

                    md.append(row(q, describe(baseTop), basePass, describe(enhTop), enhPass));

                } else {
                    // GRAPH queries: baseline has no relationship notion at all.
                    // Still run vector search to show what it returns for contrast.
                    float[] vec = embedder.embed(q.text);
                    List<SearchHit> baseHits = Searchers.searchBaseline(client, vec, 3);
                    String baseDesc = "N/A (vector-only; top hit: " +
                            (baseHits.isEmpty() ? "none" : baseHits.get(0).docId()) + ", no relation info)";
                    boolean basePass = false; // structurally cannot answer

                    List<String> related;
                    if (q.kind == QuerySpec.Kind.GRAPH_REFERENCES) {
                        related = graph.whatReferences(q.graphSubjectDocId);
                    } else {
                        related = graph.contradictionsOf(q.graphSubjectDocId);
                    }
                    boolean enhPass = related.containsAll(q.expectedRelated);
                    String enhDesc = related.isEmpty() ? "none found" : String.join(", ", related);

                    if (basePass) basslinePass++;
                    if (enhPass) enhancedPass++;

                    System.out.println("baseline: " + baseDesc + " -> FAIL (not answerable)");
                    System.out.println("enhanced (graph): " + enhDesc + " -> " + verdict(enhPass));

                    md.append(row(q, baseDesc, basePass, enhDesc, enhPass));
                }
            }
        } finally {
            client.close();
        }

        md.append("\n**Score: baseline ").append(basslinePass).append("/").append(total)
                .append(", enhanced ").append(enhancedPass).append("/").append(total).append("**\n");

        System.out.println("\n\n=== SUMMARY ===");
        System.out.println("Baseline:  " + basslinePass + "/" + total);
        System.out.println("Enhanced:  " + enhancedPass + "/" + total);

        Path outDir = Path.of("output");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("comparison.md");
        Files.writeString(outFile, md.toString());
        System.out.println("\nWrote " + outFile.toAbsolutePath());
    }

    private static boolean passesVector(SearchHit top, QuerySpec q) {
        if (top == null) {
            return false;
        }
        if (q.forbiddenDocIds.contains(top.docId())) {
            return false;
        }
        return q.expectedDocIds.contains(top.docId());
    }

    private static String describe(SearchHit hit) {
        if (hit == null) {
            return "(no result)";
        }
        return hit.docId() + " [" + hit.blockOrChunkInfo() + "] score=" + String.format("%.3f", hit.score());
    }

    private static String verdict(boolean pass) {
        return pass ? "PASS" : "FAIL";
    }

    private static String row(QuerySpec q, String baseDesc, boolean basePass, String enhDesc, boolean enhPass) {
        return "| " + q.id + " | " + escape(q.text) + " | " + escape(q.trap) + " | " + escape(baseDesc) + " | "
                + verdict(basePass) + " | " + escape(enhDesc) + " | " + verdict(enhPass) + " |\n";
    }

    private static String escape(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
