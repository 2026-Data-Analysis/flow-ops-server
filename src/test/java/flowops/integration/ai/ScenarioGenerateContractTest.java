package flowops.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioGenerateContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesNaturalLanguageScenarioGenerateRequestWithExpectedFieldNames() throws Exception {
        ScenarioGenerateRequest request = new ScenarioGenerateRequest(
                "project-1",
                "NATURAL_LANGUAGE",
                "회원가입 후 주문까지 이어지는 흐름",
                new ScenarioApiInventoryPayload(
                        "project-1",
                        List.of(new ScenarioEndpointPayload(
                                "POST:/orders",
                                "/orders",
                                "POST",
                                "Order",
                                "Create a new order",
                                objectMapper.readTree("[{\"name\":\"userId\",\"in\":\"query\"}]"),
                                new ScenarioAuthPayload("bearer", "header"),
                                objectMapper.readTree("{\"type\":\"object\"}"),
                                objectMapper.readTree("{\"type\":\"object\"}"),
                                List.of(201),
                                List.of(400, 500),
                                List.of("ORDER-400"),
                                List.of("ORDER")
                        ))
                ),
                new EnvironmentPayload("3", "dev", "https://api.example.com", "REGRESSION", "BEARER", objectMapper.nullNode(), objectMapper.nullNode()),
                null,
                null,
                2,
                5
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("project_id").asText()).isEqualTo("project-1");
        assertThat(json.get("mode").asText()).isEqualTo("NATURAL_LANGUAGE");
        assertThat(json.get("user_intent").asText()).isEqualTo("회원가입 후 주문까지 이어지는 흐름");
        assertThat(json.get("api_inventory").get("project_id").asText()).isEqualTo("project-1");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("endpoint_id").asText()).isEqualTo("POST:/orders");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("summary").asText()).isEqualTo("Order");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("description").asText()).isEqualTo("Create a new order");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("parameters").get(0).get("name").asText()).isEqualTo("userId");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("request_body_schema").get("type").asText()).isEqualTo("object");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("response_schema").get("type").asText()).isEqualTo("object");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("auth").get("type").asText()).isEqualTo("bearer");
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("expectedStatusCodes").get(0).asInt()).isEqualTo(201);
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("errorStatusCodes").get(0).asInt()).isEqualTo(400);
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("errorCodes").get(0).asText()).isEqualTo("ORDER-400");
        assertThat(json.get("environment").get("environmentId").asText()).isEqualTo("3");
        assertThat(json.get("environment").get("baseUrl").asText()).isEqualTo("https://api.example.com");
        assertThat(json.get("max_scenarios").asInt()).isEqualTo(2);
        assertThat(json.get("max_steps_per_scenario").asInt()).isEqualTo(5);
        assertThat(json.has("agent")).isFalse();
        assertThat(json.has("requestId")).isFalse();
        assertThat(json.has("apis")).isFalse();
        assertThat(json.has("existing_test_cases")).isFalse();
    }

    @Test
    void serializesRecommendScenarioGenerateRequestWithExpectedFieldNames() throws Exception {
        ScenarioGenerateRequest request = new ScenarioGenerateRequest(
                "project-1",
                "RECOMMEND",
                null,
                new ScenarioApiInventoryPayload(
                        "project-1",
                        List.of(new ScenarioEndpointPayload(
                                "POST:/orders",
                                "/orders",
                                "POST",
                                "Order",
                                null,
                                null,
                                new ScenarioAuthPayload("bearer", "header"),
                                objectMapper.readTree("{\"type\":\"object\"}"),
                                objectMapper.readTree("{\"type\":\"object\"}"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        ))
                ),
                new EnvironmentPayload("3", "dev", "https://api.example.com", "REGRESSION", "BEARER", objectMapper.nullNode(), objectMapper.nullNode()),
                List.of(new ScenarioExistingTestCasePayload(
                        "101",
                        "POST:/orders",
                        "Existing order creation",
                        "FAILURE_HANDLING",
                        "Existing saved order creation test",
                        "REGRESSION",
                        "REGRESSION",
                        objectMapper.readTree("{\"method\":\"POST\"}"),
                        objectMapper.readTree("{\"statusCode\":201}"),
                        objectMapper.readTree("{\"bodyContains\":[\"orderId\"]}"),
                        201
                )),
                null,
                3,
                null
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("project_id").asText()).isEqualTo("project-1");
        assertThat(json.get("mode").asText()).isEqualTo("RECOMMEND");
        assertThat(json.has("user_intent")).isFalse();
        assertThat(json.get("api_inventory").get("endpoints").get(0).get("endpoint_id").asText()).isEqualTo("POST:/orders");
        assertThat(json.get("existing_test_cases").get(0).get("testCaseId").asText()).isEqualTo("101");
        assertThat(json.get("existing_test_cases").get(0).get("apiId").asText()).isEqualTo("POST:/orders");
        assertThat(json.get("existing_test_cases").get(0).get("type").asText()).isEqualTo("FAILURE_HANDLING");
        assertThat(json.get("existing_test_cases").get(0).get("risk_level").asText()).isEqualTo("REGRESSION");
        assertThat(json.get("existing_test_cases").get(0).get("testLevel").asText()).isEqualTo("REGRESSION");
        assertThat(json.get("existing_test_cases").get(0).get("expected_status_code").asInt()).isEqualTo(201);
        assertThat(json.get("max_scenarios").asInt()).isEqualTo(3);
        assertThat(json.has("max_steps_per_scenario")).isFalse();
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
                            "test_level": "SANITY",
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
        assertThat(response.data().scenarios().get(0).steps().get(0).test_level()).isEqualTo("SANITY");
        assertThat(response.data().scenarios().get(0).steps().get(0).requestSpec().get("body").get("itemId").asText())
                .isEqualTo("item-123");
        assertThat(response.data().scenarios().get(0).steps().get(0).assertionSpec().get("bodyContains").get(0).asText())
                .isEqualTo("orderId");
    }
}
