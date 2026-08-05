package com.resumeai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chart helper exposed to the report templates as {@code chart}.
 *
 * <p>Bar widths are computed here rather than in the template: expression languages
 * cannot reliably aggregate over a map's value view, and doing the arithmetic in Java
 * keeps the templates declarative.
 */
public final class ReportCharts {

    public record Bar(String label, Object value, long pct) {
    }

    /** Normalises any {@code Map<String, Number>} into labelled bars scaled to the largest value. */
    public List<Bar> bars(Object source) {
        List<Bar> out = new ArrayList<>();
        if (!(source instanceof Map<?, ?> map) || map.isEmpty()) {
            return out;
        }
        double max = 0;
        for (Object v : map.values()) {
            if (v instanceof Number n) {
                max = Math.max(max, n.doubleValue());
            }
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            double v = e.getValue() instanceof Number n ? n.doubleValue() : 0;
            long pct = max > 0 ? Math.round(v * 100.0 / max) : 0;
            out.add(new Bar(humanise(String.valueOf(e.getKey())), e.getValue(), pct));
        }
        return out;
    }

    /** SCREAMING_SNAKE_CASE enum names read badly in a document. */
    private static String humanise(String key) {
        if (key.isEmpty() || key.contains("-")) {
            return key;   // score buckets like "10-20" are already fine
        }
        String spaced = key.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
