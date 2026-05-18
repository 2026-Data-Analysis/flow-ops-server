package flowops.apiinventory.domain;

import java.util.Locale;

public final class DomainTag {

    private DomainTag() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public static String fromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String segment : path.trim().split("/")) {
            if (!segment.isBlank() && !segment.startsWith("{")) {
                return normalize(segment);
            }
        }
        return null;
    }

    public static String resolve(String domainTag, String endpointPath) {
        String normalized = normalize(domainTag);
        return normalized == null ? fromPath(endpointPath) : normalized;
    }
}
