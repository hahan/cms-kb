package com.walmart.kbpoc;

import com.walmart.kbpoc.embed.EmbeddingService;

public class Main {
    public static void main(String[] args) {
        try (EmbeddingService svc = new EmbeddingService()) {
            String a = "Electronics may be returned within 30 days of purchase with a receipt.";
            String b = "You can return a TV or laptop within thirty days if you have your receipt.";
            String c = "Standard shipping typically takes 2-5 business days.";

            float[] va = svc.embed(a);
            float[] vb = svc.embed(b);
            float[] vc = svc.embed(c);

            System.out.println("sim(a,b) [should be HIGH, same topic]  = " + cosine(va, vb));
            System.out.println("sim(a,c) [should be LOW, different topic] = " + cosine(va, vc));
        }
    }

    private static double cosine(float[] x, float[] y) {
        double dot = 0;
        for (int i = 0; i < x.length; i++) {
            dot += x[i] * y[i];
        }
        return dot; // vectors are already L2-normalized, so dot product == cosine similarity
    }
}
