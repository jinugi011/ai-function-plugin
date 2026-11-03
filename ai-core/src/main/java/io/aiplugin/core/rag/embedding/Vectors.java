package io.aiplugin.core.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;

/** 벡터 계산 도우미. */
public final class Vectors {

    private Vectors() {
    }

    public static float[] toFloats(JsonNode array) {
        float[] v = new float[array.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) array.get(i).asDouble();
        }
        return v;
    }

    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimension mismatch: " + a.length + " vs " + b.length
                    + " (was the store built with a different embedding model?)");
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** pgvector 리터럴 "[0.1,0.2,...]". */
    public static String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
