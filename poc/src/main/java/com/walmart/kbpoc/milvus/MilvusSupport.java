package com.walmart.kbpoc.milvus;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;

import java.util.List;

public final class MilvusSupport {

    public static final int DIM = 384;
    public static final String BASELINE_COLLECTION = "baseline_chunks";
    public static final String ENHANCED_COLLECTION = "enhanced_blocks";

    private MilvusSupport() {
    }

    public static MilvusServiceClient connect() {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost("localhost")
                .withPort(19530)
                .build());
    }

    public static void resetCollection(MilvusClient client, String name, List<FieldType> fields) {
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(name).build());
        if (Boolean.TRUE.equals(exists.getData())) {
            client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(name).build());
        }
        client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(name)
                .withFieldTypes(fields)
                .build());
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(name)
                .withFieldName("vector")
                .withIndexType(IndexType.FLAT)
                .withMetricType(MetricType.COSINE)
                .build());
        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(name).build());
    }

    public static FieldType idField() {
        return FieldType.newBuilder().withName("id").withDataType(DataType.Int64)
                .withPrimaryKey(true).withAutoID(true).build();
    }

    public static FieldType vectorField() {
        return FieldType.newBuilder().withName("vector").withDataType(DataType.FloatVector)
                .withDimension(DIM).build();
    }

    public static FieldType varchar(String name, int maxLength) {
        return FieldType.newBuilder().withName(name).withDataType(DataType.VarChar)
                .withMaxLength(maxLength).build();
    }

    public static FieldType intField(String name) {
        return FieldType.newBuilder().withName(name).withDataType(DataType.Int64).build();
    }

    public static void insert(MilvusClient client, String collection, List<InsertParam.Field> fields) {
        client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
    }

    public static SearchResultsWrapper search(MilvusClient client, String collection, float[] queryVector,
                                               int topK, String filterExpr, List<String> outFields) {
        List<Float> vec = new java.util.ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            vec.add(v);
        }
        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.COSINE)
                .withOutFields(outFields)
                .withTopK(topK)
                .withVectors(List.of(vec))
                .withVectorFieldName("vector");
        if (filterExpr != null && !filterExpr.isBlank()) {
            builder.withExpr(filterExpr);
        }
        R<SearchResults> resp = client.search(builder.build());
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus search failed: " + resp.getMessage());
        }
        return new SearchResultsWrapper(resp.getData().getResults());
    }
}
