package flowops.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioGenerateContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesScenarioGenerateRequestWithExpectedFieldNames() throws Exception {
        ScenarioGenerateRequest request = new ScenarioGenerateRequest(
                "scenario-generator",
                "req-001",
                "qa",
                new ProjectPayload("project-1", "1", "FlowOps"),
                null,
                new MetadataPayload("ko", LocalDateTime.parse("2026-06-05T15:00:00"), "flowops"),
                new TestGenerationContext("gen-001", "RECOMMEND", "REGRESSION", null, null, "checkout"),
                List.of(new ScenarioEndpointPayload(
                        "POST:/orders",
                        "POST",
                        "/orders",
                        List.of("Order"),
                        objectMapper.readTree("{\"type\":\"object\"}"),
                        objectMapper.readTree("{\"type\":\"object\"}"),
                        new ScenarioAuthPayload("bearer", "header"),
                        false
                )),
                List.of(new ScenarioExistingTestCasePayload(
                        "101",
                        "POST:/orders",
                        "Existing order creation",
                        "HAPPY_PATH",
                        "Existing saved order creation test",
                        "REGRESSION",
                        objectMapper.readTree("{\"method\":\"POST\"}"),
                        objectMapper.readTree("{\"statusCode\":201}"),
                        objectMapper.readTree("{\"bodyContains\":[\"orderId\"]}"),
                        201
                )),
                null
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.has("api_inventory")).isFalse();
        assertThat(json.get("apis").get(0).get("apiId").asText()).isEqualTo("POST:/orders");
        assertThat(json.get("apis").get(0).get("tags").get(0).asText()).isEqualTo("Order");
        assertThat(json.get("apis").get(0).get("request_body_schema").get("type").asText()).isEqualTo("object");
        assertThat(json.get("apis").get(0).get("response_schema").get("type").asText()).isEqualTo("object");
        assertThat(json.get("apis").get(0).get("auth").get("type").asText()).isEqualTo("bearer");
        assertThat(json.get("apis").get(0).get("auth").get("location").asText()).isEqualTo("header");
        assertThat(json.get("apis").get(0).has("requestSchema")).isFalse();
        assertThat(json.get("apis").get(0).has("responseSchema")).isFalse();
        assertThat(json.get("apis").get(0).has("authRequired")).isFalse();
        assertThat(json.get("existing_test_cases").get(0).get("test_case_id").asText()).isEqualTo("101");
        assertThat(json.get("existing_test_cases").get(0).get("endpoint_id").asText()).isEqualTo("POST:/orders");
        assertThat(json.get("existing_test_cases").get(0).get("expected_status_code").asInt()).isEqualTo(201);
    }

    @Test
    void deserializesScenarioStepsFromDataScenarios() throws Exception {
        String body = """
                {
                  "requestId": "req-001",
                  "generationId": "gen-001",
                  "success": true,
                  "data": {
                    "scenarios": [
                      {
                        "name": "Order flow",
                        "type": "HAPPY_PATH",
                        "steps": [
                          {
                            "apiId": "POST:/orders",
                            "order": 1,
                            "title": "Create order",
                            "requestSpec": {"method": "POST", "body": {"itemId": "item-123"}},
                            "expectedSpec": {"statusCode": 201},
                            "assertionSpec": {"bodyContains": ["orderId"]},
                            "duplicate": false
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        ScenarioGenerateResponse response = objectMapper.readValue(body, ScenarioGenerateResponse.class);

        assertThat(response.requestId()).isEqualTo("req-001");
        assertThat(response.data().scenarios()).hasSize(1);
        assertThat(response.data().scenarios().get(0).steps().get(0).title()).isEqualTo("Create order");
        assertThat(response.data().scenarios().get(0).steps().get(0).requestSpec().get("body").get("itemId").asText())
                .isEqualTo("item-123");
        assertThat(response.data().scenarios().get(0).steps().get(0).assertionSpec().get("bodyContains").get(0).asText())
                .isEqualTo("orderId");
    }
}
