package flowops.apiinventory.service;

import flowops.apiinventory.domain.entity.ApiHttpMethod;

public record ParsedApiOperation(
        ApiHttpMethod method,
        String endpointPath,
        String operationId,
        String summary,
        String specVersion,
        boolean authRequired
) {
}
