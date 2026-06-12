package flowops.scenario.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ScenarioStepDraftRequest(
        Integer order,
        Long apiInventoryId,
        Long apiEndpointId,
        @JsonAlias({"endpoint_id"})
        String endpointId,
        String apiId,
        String method,
        String path,
        String name,
        String title,
        String description,
        JsonNode requestSpec,
        JsonNode expectedSpec,
        JsonNode assertionSpec,
        @JsonAlias({"static_payload"})
        JsonNode staticPayload,
        @JsonAlias({"static_params"})
        JsonNode staticParams,
        @JsonAlias({"expected_status_code"})
        Integer expectedStatusCode,
        @JsonAlias({"expected_assertions"})
        List<String> expectedAssertions,
        @JsonAlias({"chained_variables"})
        JsonNode chainedVariables
) {
}
