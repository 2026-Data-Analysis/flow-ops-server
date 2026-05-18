package flowops.execution.dto.response;

import flowops.execution.domain.entity.ExecutionStepLog;
import io.swagger.v3.oas.annotations.media.Schema;

public record ExecutionStepLogDetailResponse(
        @Schema(description = "요청 바디", example = "{\"email\":\"test@example.com\"}")
        String requestBody,
        @Schema(description = "응답 바디", example = "{\"result\":\"success\"}")
        String responseBody
) {

    public static ExecutionStepLogDetailResponse from(ExecutionStepLog log) {
        return new ExecutionStepLogDetailResponse(
                log.getRequestBody(),
                log.getResponseBody()
        );
    }
}
