package flowops.testcase.dto.response;

import flowops.execution.support.ExecutionRequestSpecSupport;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record TestCaseDetailResponse(
        @Schema(description = "Test case ID", example = "501")
        Long id,
        @Schema(description = "App ID", example = "1")
        Long appId,
        @Schema(description = "Selected API ID. Inventory ID when available, otherwise endpoint PK.", example = "2248")
        Long apiId,
        @Schema(description = "Project ID for inventory detail lookup.", example = "1")
        Long projectId,
        @Schema(description = "API inventory ID. Use with /projects/{projectId}/api-inventories/{inventoryId}.", example = "2248")
        Long apiInventoryId,
        @Schema(description = "Legacy API endpoint PK.", example = "10")
        Long apiEndpointId,
        @Schema(description = "Stable endpoint key in METHOD:/path format.", example = "POST:/orders")
        String endpointId,
        @Schema(description = "Test case name", example = "Order creation succeeds")
        String name,
        @Schema(description = "HTTP method used for execution.", example = "GET")
        String executionMethod,
        @Schema(description = "Endpoint path used for execution.", example = "/orders")
        String executionEndpoint,
        @Schema(description = "Description", example = "Verifies order creation succeeds.")
        String description,
        @Schema(description = "Test case type", example = "HAPPY_PATH")
        TestCaseType type,
        @Schema(description = "Test level", example = "REGRESSION")
        TestLevel testLevel,
        @Schema(description = "Source", example = "EDITED")
        TestCaseSource source,
        @Schema(description = "User role", example = "CUSTOMER")
        String userRole,
        @Schema(description = "State condition", example = "Signed in")
        String stateCondition,
        @Schema(description = "Data variant", example = "valid-card")
        String dataVariant,
        @Schema(description = "Request spec", example = "{\"amount\":10000,\"currency\":\"KRW\"}")
        String requestSpec,
        @Schema(description = "Expected spec", example = "{\"status\":200,\"approved\":true}")
        String expectedSpec,
        @Schema(description = "Assertion spec", example = "{\"assertions\":[\"status == 200\"]}")
        String assertionSpec,
        @Schema(description = "Active flag", example = "true")
        boolean active,
        @Schema(description = "Current version", example = "3")
        Integer version,
        @Schema(description = "Created timestamp", example = "2026-04-10T10:00:00")
        LocalDateTime createdAt,
        @Schema(description = "Updated timestamp", example = "2026-04-12T02:30:00")
        LocalDateTime updatedAt
) {

    public static TestCaseDetailResponse from(TestCase testCase) {
        Long apiInventoryId = testCase.getApiInventory() == null ? null : testCase.getApiInventory().getId();
        Long apiEndpointId = testCase.getApiEndpoint().getId();
        Long projectId = testCase.getApiInventory() == null || testCase.getApiInventory().getProject() == null
                ? null
                : testCase.getApiInventory().getProject().getId();
        String executionMethod = executionMethod(testCase);
        String executionEndpoint = executionEndpoint(testCase);
        return new TestCaseDetailResponse(
                testCase.getId(),
                testCase.getApp().getId(),
                apiInventoryId == null ? apiEndpointId : apiInventoryId,
                projectId,
                apiInventoryId,
                apiEndpointId,
                executionMethod + ":" + executionEndpoint,
                testCase.getName(),
                executionMethod,
                executionEndpoint,
                testCase.getDescription(),
                testCase.getType(),
                testCase.getTestLevel(),
                testCase.getSource(),
                testCase.getUserRole(),
                testCase.getStateCondition(),
                testCase.getDataVariant(),
                testCase.getRequestSpec(),
                testCase.getExpectedSpec(),
                testCase.getAssertionSpec(),
                testCase.isActive(),
                testCase.getVersion(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt()
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
}
