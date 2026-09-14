package com.walmart.kbpoc.pipeline;

import com.walmart.kbpoc.clean.HtmlCleaner;
import com.walmart.kbpoc.embed.EmbeddingService;
import com.walmart.kbpoc.llm.AnthropicClient;
import com.walmart.kbpoc.llm.LlmBlock;
import com.walmart.kbpoc.milvus.MilvusSupport;
import com.walmart.kbpoc.util.Corpus;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.InsertParam;

import java.util.ArrayList;
import java.util.List;

/**
 * ENHANCED pipeline: LLM-based block segmentation + entity extraction (see
 * docs/poc-scope.md), governance/category/region metadata reused from the
 * CMS envelope (metadata.json standing in for fields a real CMS publish
 * workflow already carries), heading-breadcrumb-prefixed embeddings, and
 * scalar metadata in Milvus for hybrid filter+vector retrieval.
 */
public class IngestEnhanced {

    public static void main(String[] args) {
        Corpus corpus = new Corpus();
        var metadata = corpus.loadMetadata();
        AnthropicClient llm = new AnthropicClient();

        List<String> docIds = new ArrayList<>();
        List<String> docTypes = new ArrayList<>();
        List<String> blockIds = new ArrayList<>();
        List<String> blockTypes = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<String> regions = new ArrayList<>();
        List<String> effectiveDates = new ArrayList<>();
        List<Long> versions = new ArrayList<>();
        List<String> supersededBys = new ArrayList<>();
        List<String> sensitivities = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<String> htmls = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();

        try (EmbeddingService embedder = new EmbeddingService()) {
            for (String docId : corpus.docIds()) {
                var env = metadata.get(docId);
                HtmlCleaner.Cleaned cleaned = HtmlCleaner.clean(corpus.rawHtml(docId));

                List<LlmBlock> blocks = llm.segment(env.docType(), env.title(), cleaned.contentHtml());
                System.out.println(docId + " -> " + blocks.size() + " LLM-segmented blocks");

                for (LlmBlock b : blocks) {
                    String blockId = docId + "-b" + b.order;
                    String breadcrumb = env.title() + (b.heading.isBlank() ? "" : " > " + b.heading);
                    String embeddingText = breadcrumb + "\n" + b.text;

                    float[] vec = embedder.embed(embeddingText);
                    List<Float> vecList = new ArrayList<>(vec.length);
                    for (float v : vec) {
                        vecList.add(v);
                    }

                    docIds.add(docId);
                    docTypes.add(env.docType());
                    blockIds.add(blockId);
                    blockTypes.add(b.blockType);
                    categories.add(String.join("|", env.category()));
                    regions.add(String.join("|", env.region()));
                    effectiveDates.add(env.governance().effectiveDate);
                    versions.add((long) env.governance().version);
                    supersededBys.add(env.governance().supersededBy == null ? "" : env.governance().supersededBy);
                    sensitivities.add(env.governance().sensitivity);
                    texts.add(embeddingText);
                    htmls.add(toDisplayHtml(b));
                    vectors.add(vecList);
                }
            }
        }

        MilvusServiceClient client = MilvusSupport.connect();
        try {
            MilvusSupport.resetCollection(client, MilvusSupport.ENHANCED_COLLECTION, List.of(
                    MilvusSupport.idField(),
                    MilvusSupport.vectorField(),
                    MilvusSupport.varchar("doc_id", 128),
                    MilvusSupport.varchar("doc_type", 32),
                    MilvusSupport.varchar("block_id", 160),
                    MilvusSupport.varchar("block_type", 64),
                    MilvusSupport.varchar("category", 256),
                    MilvusSupport.varchar("region", 64),
                    MilvusSupport.varchar("effective_date", 32),
                    MilvusSupport.intField("version"),
                    MilvusSupport.varchar("superseded_by", 128),
                    MilvusSupport.varchar("sensitivity", 32),
                    MilvusSupport.varchar("text", 2000),
                    MilvusSupport.varchar("html", 4000)
            ));

            MilvusSupport.insert(client, MilvusSupport.ENHANCED_COLLECTION, List.of(
                    new InsertParam.Field("doc_id", docIds),
                    new InsertParam.Field("doc_type", docTypes),
                    new InsertParam.Field("block_id", blockIds),
                    new InsertParam.Field("block_type", blockTypes),
                    new InsertParam.Field("category", categories),
                    new InsertParam.Field("region", regions),
                    new InsertParam.Field("effective_date", effectiveDates),
                    new InsertParam.Field("version", versions),
                    new InsertParam.Field("superseded_by", supersededBys),
                    new InsertParam.Field("sensitivity", sensitivities),
                    new InsertParam.Field("text", texts),
                    new InsertParam.Field("html", htmls),
                    new InsertParam.Field("vector", vectors)
            ));
            System.out.println("Inserted " + docIds.size() + " enhanced blocks into Milvus.");
        } finally {
            client.close();
        }
    }

    /** Normalized (not verbatim) display fragment: semantic wrapper + entity values bolded. */
    private static String toDisplayHtml(LlmBlock b) {
        String escaped = b.text
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        for (LlmBlock.LlmEntity e : b.entities) {
            String needle = "dollar_threshold".equals(e.entityType) ? ("$" + e.value) : (e.value + " " + e.unit);
            escaped = escaped.replace(needle, "<strong>" + needle + "</strong>");
        }
        String heading = b.heading.isBlank() ? "" : "<h4>" + b.heading + "</h4>";
        return "<div class=\"block-" + b.blockType.replaceAll("[^a-zA-Z0-9_-]", "") + "\">" + heading
                + "<p>" + escaped + "</p></div>";
    }
}
