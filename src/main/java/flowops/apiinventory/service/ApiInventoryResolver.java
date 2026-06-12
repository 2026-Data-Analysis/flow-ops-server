package flowops.apiinventory.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.app.domain.entity.App;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiInventoryResolver {

    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiEndpointService apiEndpointService;

    public Optional<ResolvedApiEndpoint> resolve(
            App app,
            ApiInventoryResolveRequest request,
            List<ApiInventory> availableInventories
    ) {
        List<ApiInventory> inventories = availableInventories == null ? List.of() : availableInventories;
        log.info("Resolving generated draft endpoint. projectId={}, appId={}, draftApiId={}",
                request == null ? null : request.projectId(),
                request == null ? appId(app) : request.appId(),
                request == null ? null : request.apiId());

        Optional<ResolvedApiEndpoint> resolved = resolveInternal(app, request, inventories);
        resolved.ifPresentOrElse(
                endpoint -> log.info("Resolved generated draft endpoint. endpointId={}, apiInventoryId={}, apiEndpointId={}, method={}, path={}",
                        endpoint.endpointId(),
                        endpoint.apiInventoryId(),
                        endpoint.apiEndpointId(),
                        endpoint.method(),
                        endpoint.path()),
                () -> log.warn("Failed to resolve generated draft endpoint. draftApiId={}, projectId={}, appId={}, availableInventoryIds={}, availableEndpointIds={}, availableMethodPaths={}",
                        request == null ? null : request.apiId(),
                        request == null ? null : request.projectId(),
                        request == null ? appId(app) : request.appId(),
                        availableInventoryIds(inventories),
                        availableEndpointIds(app),
                        availableMethodPaths(inventories))
        );
        return resolved;
    }

    public List<Long> availableInventoryIds(List<ApiInventory> inventories) {
        return (inventories == null ? List.<ApiInventory>of() : inventories).stream()
                .map(ApiInventory::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<String> availableMethodPaths(List<ApiInventory> inventories) {
        return (inventories == null ? List.<ApiInventory>of() : inventories).stream()
                .map(this::methodPath)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Optional<ResolvedApiEndpoint> resolveInternal(
            App app,
            ApiInventoryResolveRequest request,
            List<ApiInventory> inventories
    ) {
        if (app == null || request == null) {
            return Optional.empty();
        }
        Optional<ResolvedApiEndpoint> byInventoryId = findInventory(inventories, request.apiInventoryId())
                .map(inventory -> fromInventory(app, inventory));
        if (byInventoryId.isPresent()) {
            return byInventoryId;
        }

        Optional<ResolvedApiEndpoint> byEndpointId = findInventoryByEndpointId(app, inventories, request.endpointId());
        if (byEndpointId.isPresent()) {
            return byEndpointId;
        }

        Optional<ResolvedApiEndpoint> byApiIdMethodPath = findInventoryByEndpointId(app, inventories, methodPathOrNull(request.apiId()));
        if (byApiIdMethodPath.isPresent()) {
            return byApiIdMethodPath;
        }

        Optional<ResolvedApiEndpoint> byRequestMethodPath = findInventoryByEndpointId(app, inventories, methodPath(request.method(), request.path()));
        if (byRequestMethodPath.isPresent()) {
            return byRequestMethodPath;
        }

        Optional<ResolvedApiEndpoint> byNormalizedRequestMethodPath = findInventoryByEndpointId(
                app,
                inventories,
                methodPath(request.method(), normalizeDuplicatedPath(request.path()))
        );
        if (byNormalizedRequestMethodPath.isPresent()) {
            log.info("Normalized duplicated endpoint path. original={}, normalized={}",
                    request.path(),
                    normalizeDuplicatedPath(request.path()));
            return byNormalizedRequestMethodPath;
        }

        Long numericDraftApiId = parseLongOrNull(request.apiId());
        Optional<ResolvedApiEndpoint> byNumericInventoryId = findInventory(inventories, numericDraftApiId)
                .map(inventory -> fromInventory(app, inventory));
        if (byNumericInventoryId.isPresent()) {
            return byNumericInventoryId;
        }

        if (request.apiEndpointId() != null) {
            return apiEndpointRepository.findById(request.apiEndpointId())
                    .filter(endpoint -> endpoint.getApp() != null)
                    .filter(endpoint -> Objects.equals(endpoint.getApp().getId(), app.getId()))
                    .map(endpoint -> fromEndpoint(endpoint, null));
        }
        Optional<ResolvedApiEndpoint> byEndpointMethodPath = findEndpointByMethodPath(app, methodPath(request.method(), request.path()));
        if (byEndpointMethodPath.isPresent()) {
            return byEndpointMethodPath;
        }
        Optional<ResolvedApiEndpoint> byNormalizedEndpointMethodPath = findEndpointByMethodPath(
                app,
                methodPath(request.method(), normalizeDuplicatedPath(request.path()))
        );
        if (byNormalizedEndpointMethodPath.isPresent()) {
            return byNormalizedEndpointMethodPath;
        }
        return fuzzyMatchByActionName(app, inventories, firstNonBlank(request.endpointId(), request.apiId()));
    }

    private Optional<ResolvedApiEndpoint> findInventoryByEndpointId(App app, List<ApiInventory> inventories, String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeEndpointId(endpointId);
        return inventories.stream()
                .filter(inventory -> normalized.equals(normalizeEndpointId(methodPath(inventory))))
                .findFirst()
                .map(inventory -> fromInventory(app, inventory));
    }

    private Optional<ApiInventory> findInventory(List<ApiInventory> inventories, Long inventoryId) {
        if (inventoryId == null) {
            return Optional.empty();
        }
        return inventories.stream()
                .filter(inventory -> Objects.equals(inventory.getId(), inventoryId))
                .findFirst();
    }

    private Optional<ResolvedApiEndpoint> findEndpointByMethodPath(App app, String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            return Optional.empty();
        }
        EndpointTarget target = parseEndpointTarget(endpointId);
        if (target == null) {
            return Optional.empty();
        }
        return apiEndpointRepository.findFirstByAppIdAndMethodAndPath(app.getId(), target.method(), target.path())
                .map(endpoint -> fromEndpoint(endpoint, null));
    }

    private ResolvedApiEndpoint fromInventory(App app, ApiInventory inventory) {
        ApiEndpoint endpoint = apiEndpointService.findOrCreateFromInventory(app, inventory);
        return fromEndpoint(endpoint, inventory);
    }

    private ResolvedApiEndpoint fromEndpoint(ApiEndpoint endpoint, ApiInventory inventory) {
        ApiMethod method = inventory == null ? endpoint.getMethod() : ApiMethod.valueOf(inventory.getMethod().name());
        String path = inventory == null ? endpoint.getPath() : inventory.getEndpointPath();
        return new ResolvedApiEndpoint(
                inventory == null ? null : inventory.getId(),
                endpoint.getId(),
                methodPath(method.name(), path),
                method,
                path,
                endpoint,
                inventory
        );
    }

    private List<Long> availableEndpointIds(App app) {
        if (app == null || app.getId() == null) {
            return List.of();
        }
        return apiEndpointRepository.findByAppId(app.getId()).stream()
                .map(ApiEndpoint::getId)
                .toList();
    }

    private String methodPath(ApiInventory inventory) {
        if (inventory == null || inventory.getMethod() == null || inventory.getEndpointPath() == null) {
            return null;
        }
        return methodPath(inventory.getMethod().name(), inventory.getEndpointPath());
    }

    private String methodPath(String method, String path) {
        if (method == null || method.isBlank() || path == null || path.isBlank()) {
            return null;
        }
        return method.trim().toUpperCase() + ":" + path.trim();
    }

    private String methodPathOrNull(String value) {
        return parseEndpointTarget(value) == null ? null : normalizeEndpointId(value);
    }

    private String normalizeEndpointId(String value) {
        EndpointTarget target = parseEndpointTarget(value);
        return target == null ? value == null ? null : value.trim() : methodPath(target.method().name(), normalizeDuplicatedPath(target.path()));
    }

    private EndpointTarget parseEndpointTarget(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }
        try {
            ApiMethod method = ApiMethod.valueOf(value.substring(0, separator).trim().toUpperCase());
            String path = value.substring(separator + 1).trim();
            return path.isBlank() ? null : new EndpointTarget(method, path);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Optional<ResolvedApiEndpoint> fuzzyMatchByActionName(App app, List<ApiInventory> inventories, String actionName) {
        String normalizedAction = normalizeSearchText(actionName);
        if (normalizedAction == null) {
            return Optional.empty();
        }
        return inventories.stream()
                .filter(inventory -> matchesActionName(inventory, normalizedAction))
                .findFirst()
                .map(inventory -> fromInventory(app, inventory));
    }

    private boolean matchesActionName(ApiInventory inventory, String normalizedAction) {
        return List.of(inventory.getOperationId(), inventory.getSummary(), inventory.getEndpointPath()).stream()
                .map(this::normalizeSearchText)
                .filter(Objects::nonNull)
                .anyMatch(candidate -> candidate.equals(normalizedAction)
                        || candidate.contains(normalizedAction)
                        || normalizedAction.contains(candidate));
    }

    private String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        EndpointTarget target = parseEndpointTarget(value);
        String raw = target == null ? value : target.path();
        String normalized = raw.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9가-힣]+", " ")
                .trim()
                .toLowerCase();
        return normalized.isBlank() ? null : normalized.replace(" ", "");
    }

    private String normalizeDuplicatedPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String trimmed = path.trim();
        String[] segments = trimmed.split("/");
        List<String> nonBlankSegments = java.util.Arrays.stream(segments)
                .filter(segment -> !segment.isBlank())
                .toList();
        if (nonBlankSegments.size() < 2 || nonBlankSegments.size() % 2 != 0) {
            return trimmed;
        }
        int half = nonBlankSegments.size() / 2;
        if (!nonBlankSegments.subList(0, half).equals(nonBlankSegments.subList(half, nonBlankSegments.size()))) {
            return trimmed;
        }
        return "/" + String.join("/", nonBlankSegments.subList(0, half));
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private Long parseLongOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long appId(App app) {
        return app == null ? null : app.getId();
    }

    private record EndpointTarget(ApiMethod method, String path) {
    }
}
