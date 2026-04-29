package flowops.apiinventory.service;

import java.util.List;

public record ParsedOpenApiSpec(
        String toolName,
        String toolVersion,
        List<ParsedApiOperation> operations
) {
    public static ParsedOpenApiSpec empty() {
        return new ParsedOpenApiSpec(null, null, List.of());
    }
}
