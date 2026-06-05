package flowops.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioGenerateContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesScenarioGenerateRequestWithPdfSchemaFieldNames() throws Exception {
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
                        "Order",
                        objectMapper.readTree("{\"type\":\"object\"}"),
                        objectMapper.readTree("{\"type\":\"object\"}"),
                        true,
                        false
                )),
                null
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.has("api_inventory")).isFalse();
        assertThat(json.has("existing_test_cases")).isFalse();
        assertThat(json.get("apis").get(0).has("request_body_schema")).isFalse();
        assertThat(json.get("apis").get(0).get("apiId").asText()).isEqualTo("POST:/orders");
        assertThat(json.get("apis").get(0).get("requestSchema").get("type").asText()).isEqualTo("object");
        assertThat(json.get("apis").get(0).get("responseSchema").get("type").asText()).isEqualTo("object");
    }

    @Test
    void deserializesScenarioStepsWithDraftLikeSpecFields() throws Exception {
        String body = """
                {
                  "requestId": "req-001",
                  "generationId": "gen-001",
                  "scenarios": [
                    {
                      "name": "Order flow",
                      "type": "HAPPY_PATH",
                      "steps": [
                        {
                          "apiId": "POST:/orders",
                          "order": 1,
                          "title": "정상 주문 생성",
                          "requestSpec": {"method": "POST", "body": {"itemId": "item-123"}},
                          "expectedSpec": {"statusCode": 201},
                          "assertionSpec": {"bodyContains": ["orderId"]},
                          "duplicate": false
                        }
                      ]
                    }
                  ]
                }
                """;

        ScenarioGenerateResponse response = objectMapper.readValue(body, ScenarioGenerateResponse.class);

        assertThat(response.requestId()).isEqualTo("req-001");
        assertThat(response.scenarios()).hasSize(1);
        assertThat(response.scenarios().get(0).steps().get(0).title()).isEqualTo("정상 주문 생성");
        assertThat(response.scenarios().get(0).steps().get(0).requestSpec().get("body").get("itemId").asText())
                .isEqualTo("item-123");
        assertThat(response.scenarios().get(0).steps().get(0).assertionSpec().get("bodyContains").get(0).asText())
                .isEqualTo("orderId");
    }
}
