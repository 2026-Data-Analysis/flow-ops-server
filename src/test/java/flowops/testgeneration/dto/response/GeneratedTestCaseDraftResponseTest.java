package flowops.testgeneration.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.domain.entity.ApiInventoryStatus;
import flowops.app.domain.entity.App;
import flowops.project.domain.entity.Project;
import flowops.project.domain.entity.ProjectStatus;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.domain.entity.TestGenerationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GeneratedTestCaseDraftResponseTest {

    @Test
    void fromReturnsEndpointDisplayMetadataAndStructuredRequestBody() {
        GeneratedTestCaseDraft draft = draft("""
                {"productId":1,"quantity":2}
                """);

        GeneratedTestCaseDraftResponse response = GeneratedTestCaseDraftResponse.from(draft);

        assertThat(response.endpointName()).isEqualTo("POST /orders");
        assertThat(response.selectedEndpoint().id()).isEqualTo(2056L);
        assertThat(response.selectedEndpoint().path()).isEqualTo("/orders");
        assertThat(response.risk_level()).isEqualTo("REGRESSION");
        assertThat(response.request().method()).isEqualTo("POST");
        assertThat(response.request().endpoint()).isEqualTo("/orders");
        assertThat(response.request().body().get("productId").asInt()).isEqualTo(1);
        assertThat(response.expected().get("status").asInt()).isEqualTo(201);
        assertThat(response.assertion().get("assertions").get(0).asText()).isEqualTo("status == 201");
        assertThat(response.requestSpec()).contains("\"body\":{\"productId\":1,\"quantity\":2}");
    }

    @Test
    void fromPreservesExplicitRequestBodyAliasesInNormalizedRequestSpec() {
        GeneratedTestCaseDraft draft = draft("""
                {
                  "execution_method": "PATCH",
                  "execution_endpoint": "/orders/1",
                  "headers": {"Authorization": "Bearer token"},
                  "query": {"dryRun": true},
                  "requestBody": {"status": "PAID"}
                }
                """);

        GeneratedTestCaseDraftResponse response = GeneratedTestCaseDraftResponse.from(draft);

        assertThat(response.endpointName()).isEqualTo("PATCH /orders/1");
        assertThat(response.request().headers()).containsEntry("Authorization", "Bearer token");
        assertThat(response.request().queryParams()).containsEntry("dryRun", "true");
        assertThat(response.request().body().get("status").asText()).isEqualTo("PAID");
        assertThat(response.requestSpec()).contains("\"endpoint\":\"/orders/1\"");
        assertThat(response.requestSpec()).contains("\"body\":{\"status\":\"PAID\"}");
    }

    @Test
    void fromUsesInventoryIdWhenDraftHasInventoryAndKeepsEndpointMetadata() {
        GeneratedTestCaseDraft draft = draft("""
                {"productId":1,"quantity":2}
                """, true);

        GeneratedTestCaseDraftResponse response = GeneratedTestCaseDraftResponse.from(draft);

        assertThat(response.apiId()).isEqualTo(2241L);
        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.apiInventoryId()).isEqualTo(2241L);
        assertThat(response.apiEndpointId()).isEqualTo(2056L);
        assertThat(response.endpointId()).isEqualTo("POST:/orders");
        assertThat(response.selectedEndpoint().id()).isEqualTo(2241L);
        assertThat(response.selectedEndpoint().projectId()).isEqualTo(1L);
        assertThat(response.selectedEndpoint().apiInventoryId()).isEqualTo(2241L);
        assertThat(response.selectedEndpoint().apiEndpointId()).isEqualTo(2056L);
    }

    private GeneratedTestCaseDraft draft(String requestSpec) {
        return draft(requestSpec, false);
    }

    private GeneratedTestCaseDraft draft(String requestSpec, boolean withInventory) {
        App app = App.builder()
                .name("shop")
                .build();
        ReflectionTestUtils.setField(app, "id", 1L);

        ApiEndpoint endpoint = ApiEndpoint.builder()
                .app(app)
                .method(ApiMethod.POST)
                .path("/orders")
                .domainTag("orders")
                .controllerName("OrderController")
                .responseSchema("{\"expectedStatusCodes\":[201]}")
                .deprecated(false)
                .build();
        ReflectionTestUtils.setField(endpoint, "id", 2056L);

        ApiInventory inventory = null;
        if (withInventory) {
            Project project = Project.builder()
                    .name("Shop")
                    .slug("shop")
                    .status(ProjectStatus.ACTIVE)
                    .build();
            ReflectionTestUtils.setField(project, "id", 1L);
            inventory = ApiInventory.builder()
                    .project(project)
                    .method(ApiHttpMethod.POST)
                    .endpointPath("/orders")
                    .operationId("createOrder")
                    .domainTag("orders")
                    .sourceType(ApiInventorySource.OPENAPI)
                    .status(ApiInventoryStatus.ACTIVE)
                    .authRequired(false)
                    .responseSchema("{\"expectedStatusCodes\":[201]}")
                    .build();
            ReflectionTestUtils.setField(inventory, "id", 2241L);
        }

        TestGeneration generation = TestGeneration.builder()
                .app(app)
                .status(TestGenerationStatus.COMPLETED)
                .requestedBy("tester")
                .existingCount(0)
                .newCount(1)
                .duplicateCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(generation, "id", 77L);

        GeneratedTestCaseDraft draft = GeneratedTestCaseDraft.builder()
                .generation(generation)
                .apiEndpoint(endpoint)
                .apiInventory(inventory)
                .title("Order creation succeeds")
                .description("Verifies order creation.")
                .type("HAPPY_PATH")
                .riskLevel("REGRESSION")
                .userRole("CUSTOMER")
                .stateCondition("Signed in")
                .dataVariant("single product")
                .requestSpec(requestSpec)
                .expectedSpec("{\"status\":201}")
                .assertionSpec("{\"assertions\":[\"status == 201\"]}")
                .duplicate(false)
                .selectedForSave(false)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(draft, "id", 1001L);
        return draft;
    }
}
