package flowops.testgeneration.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.app.domain.entity.App;
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
        assertThat(response.request().method()).isEqualTo("POST");
        assertThat(response.request().endpoint()).isEqualTo("/orders");
        assertThat(response.request().body().get("productId").asInt()).isEqualTo(1);
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

    private GeneratedTestCaseDraft draft(String requestSpec) {
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
