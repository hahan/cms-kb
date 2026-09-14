package com.walmart.kbpoc.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmart.kbpoc.milvus.MilvusSupport;
import com.walmart.kbpoc.util.Corpus;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports the ENHANCED pipeline's actual stored blocks for given doc_ids,
 * reassembled into the KbDocument shape from schemas/kb-document.schema.json,
 * so the real LLM segmentation output can be inspected directly rather than
 * reconstructed from memory.
 */
public class ExportEnhancedDoc {

    public static void main(String[] args) throws IOException {
        List<String> docIds = args.length > 0
                ? List.of(args)
                : List.of("ret-elec-v2", "sop-elec-returns", "policy-window-conflict-a");

        Corpus corpus = new Corpus();
        var metadata = corpus.loadMetadata();
        ObjectMapper mapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

        MilvusServiceClient client = MilvusSupport.connect();
        List<Map<String, Object>> allDocs = new ArrayList<>();
        try {
            for (String docId : docIds) {
                var env = metadata.get(docId);

                QueryParam queryParam = QueryParam.newBuilder()
                        .withCollectionName(MilvusSupport.ENHANCED_COLLECTION)
                        .withExpr("doc_id == \"" + docId + "\"")
                        .withOutFields(List.of("doc_id", "block_id", "block_type", "text", "html"))
                        .build();
                QueryResultsWrapper wrapper = new QueryResultsWrapper(client.query(queryParam).getData());
                List<QueryResultsWrapper.RowRecord> rows = wrapper.getRowRecords();

                List<Map<String, Object>> blocks = new ArrayList<>();
                for (QueryResultsWrapper.RowRecord row : rows) {
                    String blockId = (String) row.get("block_id");
                    String blockType = (String) row.get("block_type");
                    String fullText = (String) row.get("text");
                    String html = (String) row.get("html");

                    int order = Integer.parseInt(blockId.substring(blockId.lastIndexOf("-b") + 2));
                    String[] parts = fullText.split("\n", 2);
                    String headingPath = parts[0];
                    String text = parts.length > 1 ? parts[1] : "";

                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("block_id", blockId);
                    block.put("block_type", blockType);
                    block.put("order", order);
                    block.put("heading_path", headingPath);
                    block.put("text", text);
                    block.put("html", html);
                    block.put("block_source", "html_heuristic");
                    blocks.add(block);
                }
                blocks.sort(Comparator.comparingInt(b -> (Integer) b.get("order")));

                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("doc_id", docId);
                doc.put("doc_type", env.docType());
                doc.put("title", env.title());
                doc.put("source_format", "html");
                doc.put("category", env.category());
                doc.put("region", env.region());
                Map<String, Object> governance = new LinkedHashMap<>();
                governance.put("effective_date", env.governance().effectiveDate);
                governance.put("version", env.governance().version);
                governance.put("supersedes", env.governance().supersedes);
                governance.put("superseded_by", env.governance().supersededBy);
                governance.put("sensitivity", env.governance().sensitivity);
                doc.put("governance", governance);
                doc.put("blocks", blocks);
                allDocs.add(doc);

                System.out.println("\n" + "=".repeat(80));
                System.out.println(docId + " -> " + blocks.size() + " blocks");
                System.out.println("=".repeat(80));
                System.out.println(mapper.writeValueAsString(doc));
            }
        } finally {
            client.close();
        }

        Path outFile = Path.of("output", "enhanced-doc-examples.json");
        Files.createDirectories(outFile.getParent());
        Files.writeString(outFile, mapper.writeValueAsString(allDocs));
        System.out.println("\nWrote " + outFile.toAbsolutePath());
    }
}
