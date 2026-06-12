package flowops.testcase.dto.response;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.execution.support.ExecutionRequestSpecSupport;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;

public record TestCaseSummaryResponse(
        @Schema(description = "Test case ID", example = "501")
        Long id,
        @Schema(description = "Selected API ID. Inventory ID when available, otherwise endpoint PK.", example = "2248")
        Long apiId,
        @Schema(description = "Project ID for inventory detail lookup.", example = "1")
        Long projectId,
        @Schema(description = "API inventory ID. Use with /projects/{projectId}/api-inventories/{inventoryId}.", example = "2248")
        Long apiInventoryId,
        @Schema(description = "Legacy API endpoint PK.", example = "101")
        Long apiEndpointId,
        @Schema(description = "Stable endpoint key in METHOD:/path format.", example = "POST:/orders")
        String endpointId,
        @Schema(description = "Test case name", example = "Order creation succeeds")
        String name,
        @Schema(description = "HTTP method used for execution.", example = "GET")
        String executionMethod,
        @Schema(description = "Endpoint path used for execution.", example = "/orders")
        String executionEndpoint,
        @Schema(description = "Test case type", example = "HAPPY_PATH")
        TestCaseType type,
        @Schema(description = "Test level", example = "SMOKE")
        TestLevel testLevel,
        @Schema(description = "Description")
        String description,
        @Schema(description = "Expected result spec")
        String expectedSpec,
        @Schema(description = "Request spec")
        String requestSpec,
        @Schema(description = "Assertion spec")
        String assertionSpec,
        @Schema(description = "User role")
        String userRole,
        @Schema(description = "State condition")
        String stateCondition,
        @Schema(description = "Data variant")
        String dataVariant,
        @Schema(description = "Active flag", example = "true")
        boolean active,
        @Schema(description = "Current version", example = "3")
        Integer version,
        @Schema(description = "Selected endpoint metadata for the list query.")
        SelectedEndpointResponse selectedEndpoint
) {

    public static TestCaseSummaryResponse from(TestCase testCase, ApiEndpoint selectedEndpoint) {
        Long apiInventoryId = testCase.getApiInventory() == null ? null : testCase.getApiInventory().getId();
        Long apiEndpointId = testCase.getApiEndpoint().getId();
        Long projectId = testCase.getApiInventory() == null || testCase.getApiInventory().getProject() == null
                ? null
                : testCase.getApiInventory().getProject().getId();
        String executionMethod = executionMethod(testCase);
        String executionEndpoint = executionEndpoint(testCase);
        String endpointId = executionMethod + ":" + executionEndpoint;
        Long selectedId = apiInventoryId == null ? apiEndpointId : apiInventoryId;
        return new TestCaseSummaryResponse(
                testCase.getId(),
                selectedId,
                projectId,
                apiInventoryId,
                apiEndpointId,
                endpointId,
                testCase.getName(),
                executionMethod,
                executionEndpoint,
                testCase.getType(),
                testCase.getTestLevel(),
                testCase.getDescription(),
                testCase.getExpectedSpec(),
                testCase.getRequestSpec(),
                testCase.getAssertionSpec(),
                testCase.getUserRole(),
                testCase.getStateCondition(),
                testCase.getDataVariant(),
                testCase.isActive(),
                testCase.getVersion(),
                SelectedEndpointResponse.from(
                        selectedEndpoint,
                        selectedId,
                        projectId,
                        apiInventoryId,
                        apiEndpointId,
                        endpointId
                )
        );
    }

    private static String executionMethod(TestCase testCase) {
        String override = ExecutionRequestSpecSupport.executionMethod(testCase.getRequestSpec());
        return override == null ? testCase.getApiEndpoint().getMethod().name() : override;
    }

    private static String executionEndpoint(TestCase testCase) {
        String override = ExecutionRequestSpecSupport.executionEndpoint(testCase.getRequestSpec());
        return override == null ? testCase.getApiEndpoint().getPath() : override;
    }

    public record SelectedEndpointResponse(
            @Schema(description = "Selected API ID. Inventory ID when available, otherwise endpoint PK.", example = "2248")
            Long id,
            @Schema(description = "Project ID for inventory detail lookup.", example = "1")
            Long projectId,
            @Schema(description = "API inventory ID.", example = "2248")
            Long apiInventoryId,
            @Schema(description = "Legacy API endpoint PK.", example = "10")
            Long apiEndpointId,
            @Schema(description = "Stable endpoint key in METHOD:/path format.", example = "POST:/orders")
            String endpointId,
            @Schema(description = "HTTP method.", example = "GET")
            ApiMethod method,
            @Schema(description = "API path.", example = "/orders/{orderId}")
            String path,
            @Schema(description = "Domain tag.", example = "ORDER")
            String domainTag,
            @Schema(description = "Controller name.", example = "OrderController")
            String controllerName
    ) {
        public static SelectedEndpointResponse from(ApiEndpoint endpoint) {
            return from(endpoint, endpoint.getId(), null, null, endpoint.getId(),
                    endpoint.getMethod().name() + ":" + endpoint.getPath());
        }

        public static SelectedEndpointResponse from(
                ApiEndpoint endpoint,
                Long id,
                Long projectId,
                Long apiInventoryId,
                Long apiEndpointId,
                String endpointId
        ) {
            return new SelectedEndpointResponse(
                    id,
                    projectId,
                    apiInventoryId,
                    apiEndpointId,
                    endpointId,
                    endpoint.getMethod(),
                    endpoint.getPath(),
                    endpoint.getDomainTag(),
                    endpoint.getControllerName()
            );
        }
    }
}
