package io.github.stockflow.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class OrderLines {
    private OrderLines() {}
    public record Line(long skuId, int quantity) {}

    public static List<Line> normalize(List<Line> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 990)
            throw new IllegalArgumentException("Order must contain 1–10 distinct SKU");
        var quantities = new TreeMap<Long,Integer>();
        for (var line : lines) {
            if (line == null || line.skuId() <= 0 || line.quantity() < 1 || line.quantity() > 99)
                throw new IllegalArgumentException("SKU must be positive and quantity must be 1–99");
            int merged = quantities.getOrDefault(line.skuId(), 0) + line.quantity();
            if (merged > 99) throw new IllegalArgumentException("Merged SKU quantity exceeds 99");
            quantities.put(line.skuId(), merged);
            if (quantities.size() > 10) throw new IllegalArgumentException("At most 10 distinct SKU per order");
        }
        return quantities.entrySet().stream().map(e -> new Line(e.getKey(),e.getValue())).toList();
    }

    public static String fingerprint(List<Line> normalized) {
        var text = new StringBuilder();
        for(var line : normalized) text.append(line.skuId()).append(':').append(line.quantity()).append(';');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK",e);
        }
    }
}
