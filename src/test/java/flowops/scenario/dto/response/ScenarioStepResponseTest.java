package flowops.scenario.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.scenario.domain.entity.ScenarioStep;
import org.junit.jupiter.api.Test;

class ScenarioStepResponseTest {

    @Test
    void exposesGeneratedStepSpecFieldsOnDetailResponse() {
        ApiEndpoint endpoint = ApiEndpoint.builder()
                .method(ApiMethod.POST)
                .path("/orders")
                .deprecated(false)
                .build();
        ScenarioStep step = ScenarioStep.builder()
                .stepOrder(1)
                .apiEndpoint(endpoint)
                .label("Create order")
                .stepId("step-1")
                .ref("step_1")
                .chainedVariables("[{\"name\":\"auth_token\"}]")
                .type("HAPPY_PATH")
                .testLevel("REGRESSION")
                .userRole("CUSTOMER")
                .stateCondition("Logged in")
                .dataVariant("valid order")
                .requestSpec("{\"method\":\"POST\",\"body\":{\"itemId\":\"item-123\"}}")
                .expectedSpec("{\"statusCode\":201}")
                .assertionSpec("{\"bodyContains\":[\"orderId\"]}")
                .duplicate(false)
                .build();

        ScenarioStepResponse response = ScenarioStepResponse.from(step);

        assertThat(response.stepId()).isEqualTo("step-1");
        assertThat(response.ref()).isEqualTo("step_1");
        assertThat(response.chainedVariables().get(0).get("name").asText()).isEqualTo("auth_token");
        assertThat(response.type()).isEqualTo("HAPPY_PATH");
        assertThat(response.testLevel()).isEqualTo("REGRESSION");
        assertThat(response.userRole()).isEqualTo("CUSTOMER");
        assertThat(response.requestSpec().get("body").get("itemId").asText()).isEqualTo("item-123");
        assertThat(response.expectedSpec().get("statusCode").asInt()).isEqualTo(201);
        assertThat(response.assertionSpec().get("bodyContains").get(0).asText()).isEqualTo("orderId");
        assertThat(response.duplicate()).isFalse();
    }
}
