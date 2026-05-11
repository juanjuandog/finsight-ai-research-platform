package com.finsight.rag;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Component
public class EmbeddingService {
    private static final int DIMENSION = 16;

    public List<Double> embed(String text) {
        byte[] digest = digest(text == null ? "" : text);
        List<Double> vector = new ArrayList<>(DIMENSION);
        for (int i = 0; i < DIMENSION; i++) {
            int unsigned = Byte.toUnsignedInt(digest[i]);
            vector.add((unsigned / 127.5) - 1.0);
        }
        return vector;
    }

    public String hash(String text) {
        byte[] digest = digest(text == null ? "" : text);
        StringBuilder builder = new StringBuilder();
        for (byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private byte[] digest(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create deterministic embedding", ex);
        }
    }
}

