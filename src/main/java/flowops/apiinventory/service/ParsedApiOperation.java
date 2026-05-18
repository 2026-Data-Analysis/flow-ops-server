package flowops.apiinventory.service;

import flowops.apiinventory.domain.entity.ApiHttpMethod;

public record ParsedApiOperation(
        ApiHttpMethod method,
        String endpointPath,
        String operationId,
        String domainTag,
        String summary,
        String specVersion,
        boolean authRequired,
        String requestSchema,
        String responseSchema
) {
}
