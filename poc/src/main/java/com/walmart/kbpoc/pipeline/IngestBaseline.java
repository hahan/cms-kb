package com.walmart.kbpoc.pipeline;

import com.walmart.kbpoc.clean.FixedWindowChunker;
import com.walmart.kbpoc.clean.HtmlCleaner;
import com.walmart.kbpoc.embed.EmbeddingService;
import com.walmart.kbpoc.milvus.MilvusSupport;
import com.walmart.kbpoc.util.Corpus;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.InsertParam;

import java.util.ArrayList;
import java.util.List;

/**
 * BASELINE pipeline: strip HTML noise, fixed-window chunk, embed, insert into
 * Milvus with only doc_id/title/url as metadata. No block structure, no
 * governance fields, no filtering at query time. See docs/poc-scope.md.
 */
public class IngestBaseline {

    public static void main(String[] args) {
        Corpus corpus = new Corpus();
        var metadata = corpus.loadMetadata();

        List<String> docIds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();

        try (EmbeddingService embedder = new EmbeddingService()) {
            for (String docId : corpus.docIds()) {
                var env = metadata.get(docId);
                HtmlCleaner.Cleaned cleaned = HtmlCleaner.clean(corpus.rawHtml(docId));
                List<String> chunks = FixedWindowChunker.chunk(cleaned.plainText());
                System.out.println(docId + " -> " + chunks.size() + " baseline chunks");

                for (String chunk : chunks) {
                    float[] vec = embedder.embed(chunk);
                    List<Float> vecList = new ArrayList<>(vec.length);
                    for (float v : vec) {
                        vecList.add(v);
                    }
                    docIds.add(docId);
                    titles.add(env.title());
                    urls.add(env.sourceUrl());
                    texts.add(chunk);
                    vectors.add(vecList);
                }
            }
        }

        MilvusServiceClient client = MilvusSupport.connect();
        try {
            MilvusSupport.resetCollection(client, MilvusSupport.BASELINE_COLLECTION, List.of(
                    MilvusSupport.idField(),
                    MilvusSupport.vectorField(),
                    MilvusSupport.varchar("doc_id", 128),
                    MilvusSupport.varchar("title", 256),
                    MilvusSupport.varchar("url", 512),
                    MilvusSupport.varchar("text", 2000)
            ));

            MilvusSupport.insert(client, MilvusSupport.BASELINE_COLLECTION, List.of(
                    new InsertParam.Field("doc_id", docIds),
                    new InsertParam.Field("title", titles),
                    new InsertParam.Field("url", urls),
                    new InsertParam.Field("text", texts),
                    new InsertParam.Field("vector", vectors)
            ));
            System.out.println("Inserted " + docIds.size() + " baseline chunks into Milvus.");
        } finally {
            client.close();
        }
    }
}
