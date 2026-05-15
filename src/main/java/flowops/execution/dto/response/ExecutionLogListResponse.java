package flowops.execution.dto.response;

import java.util.List;

public record ExecutionLogListResponse(
        List<ExecutionLogListItemResponse> items
) {
}
