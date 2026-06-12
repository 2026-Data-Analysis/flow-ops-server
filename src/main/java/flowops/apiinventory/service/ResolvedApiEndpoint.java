package flowops.apiinventory.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.apiinventory.domain.entity.ApiInventory;

public record ResolvedApiEndpoint(
        Long apiInventoryId,
        Long apiEndpointId,
        String endpointId,
        ApiMethod method,
        String path,
        ApiEndpoint apiEndpoint,
        ApiInventory apiInventory
) {
}
