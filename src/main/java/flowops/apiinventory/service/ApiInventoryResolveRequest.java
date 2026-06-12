package flowops.apiinventory.service;

public record ApiInventoryResolveRequest(
        Long projectId,
        Long appId,
        Long apiInventoryId,
        Long apiEndpointId,
        String endpointId,
        String apiId,
        String method,
        String path
) {
}
