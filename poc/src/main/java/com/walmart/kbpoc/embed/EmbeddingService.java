package com.walmart.kbpoc.embed;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.nio.file.Path;

/**
 * Local, fully offline-after-first-download embedding model via DJL + ONNX
 * Runtime (no API key, no per-call network cost). Same model is used for
 * both the baseline and enhanced pipelines, so any retrieval-quality
 * difference between them comes from chunking/structure, not a stronger
 * embedding model.
 *
 * Uses a hand-written Translator rather than DJL's built-in
 * TextEmbeddingTranslatorFactory: this particular ONNX export of
 * all-MiniLM-L6-v2 already returns a pooled [1, 384] sentence embedding, and
 * the factory's automatic mean-pooling step assumes an unpooled per-token
 * output and throws (DimOutOfRange on a [1, 384] tensor). We just take the
 * model's own output directly and L2-normalize it.
 */
public class EmbeddingService implements AutoCloseable {

    private static final String MODEL_URL =
            "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2";

    private final ZooModel<String, float[]> model;
    private final Predictor<String, float[]> predictor;
    private final HuggingFaceTokenizer tokenizer;

    public EmbeddingService() {
        try {
            // Phase 1: trigger the download (if not already cached) with a no-op
            // translator just to resolve the local model directory, so we can
            // build a HuggingFaceTokenizer from local files rather than hitting
            // a DJL tokenizer-download-by-repo-id path that errors on this setup.
            Criteria<NDList, NDList> bootstrap = Criteria.builder()
                    .setTypes(NDList.class, NDList.class)
                    .optModelUrls(MODEL_URL)
                    .optEngine("OnnxRuntime")
                    .optTranslator(new NoopTranslator())
                    .optProgress(new ai.djl.training.util.ProgressBar())
                    .build();
            Path modelPath;
            try (ZooModel<NDList, NDList> bootstrapModel = bootstrap.loadModel()) {
                modelPath = bootstrapModel.getModelPath();
            }

            this.tokenizer = HuggingFaceTokenizer.newInstance(modelPath);

            // Phase 2: real typed load, reusing the now-cached model files.
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls(MODEL_URL)
                    .optEngine("OnnxRuntime")
                    .optTranslator(new PooledEmbeddingTranslator(tokenizer))
                    .build();
            this.model = criteria.loadModel();
            this.predictor = model.newPredictor();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load local embedding model: " + e.getMessage(), e);
        }
    }

    public synchronized float[] embed(String text) {
        try {
            return predictor.predict(text);
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        predictor.close();
        model.close();
        tokenizer.close();
    }

    /**
     * Tokenize -> run ONNX model -> mean-pool the per-token output over
     * non-padding tokens (mask-weighted) -> L2 normalize. Done in plain Java
     * because OnnxRuntime's NDArray backend does not implement general
     * tensor ops (sum/div/norm all throw UnsupportedOperationException) --
     * that gap, not a pooling-logic bug, is what made DJL's built-in
     * TextEmbeddingTranslatorFactory fail for this engine.
     */
    private static class PooledEmbeddingTranslator implements Translator<String, float[]> {
        private static final int HIDDEN_SIZE = 384; // all-MiniLM-L6-v2

        private final HuggingFaceTokenizer tokenizer;

        PooledEmbeddingTranslator(HuggingFaceTokenizer tokenizer) {
            this.tokenizer = tokenizer;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, String input) {
            NDManager manager = ctx.getNDManager();
            Encoding encoding = tokenizer.encode(input);
            long[] mask = encoding.getAttentionMask();
            ctx.setAttachment("attentionMask", mask);

            NDArray inputIds = manager.create(encoding.getIds()).toType(DataType.INT64, false).expandDims(0);
            NDArray attentionMask = manager.create(mask).toType(DataType.INT64, false).expandDims(0);
            inputIds.setName("input_ids");
            attentionMask.setName("attention_mask");
            return new NDList(inputIds, attentionMask);
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            long[] mask = (long[]) ctx.getAttachment("attentionMask");
            float[] tokenEmbeddings = list.get(0).toFloatArray(); // flat [1, seqLen, 384] -> seqLen*384
            int seqLen = tokenEmbeddings.length / HIDDEN_SIZE;

            float[] pooled = new float[HIDDEN_SIZE];
            long validTokens = 0;
            for (int t = 0; t < seqLen; t++) {
                if (mask[t] == 0) {
                    continue;
                }
                validTokens++;
                int base = t * HIDDEN_SIZE;
                for (int d = 0; d < HIDDEN_SIZE; d++) {
                    pooled[d] += tokenEmbeddings[base + d];
                }
            }
            if (validTokens == 0) {
                validTokens = 1;
            }
            double sumSquares = 0;
            for (int d = 0; d < HIDDEN_SIZE; d++) {
                pooled[d] /= validTokens;
                sumSquares += (double) pooled[d] * pooled[d];
            }
            float norm = (float) Math.sqrt(sumSquares);
            if (norm > 0f) {
                for (int d = 0; d < HIDDEN_SIZE; d++) {
                    pooled[d] /= norm;
                }
            }
            return pooled;
        }

        @Override
        public Batchifier getBatchifier() {
            return null; // batch=1 per call, handled directly in processInput
        }
    }
}
