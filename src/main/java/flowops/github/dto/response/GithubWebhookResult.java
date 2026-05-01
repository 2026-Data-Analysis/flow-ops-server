package flowops.github.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GithubWebhookResult(
        @Schema(description = "GitHub 이벤트 유형", example = "pull_request")
        String event,

        @Schema(description = "매칭된 저장소 전체 이름", example = "flowops/backend")
        String repositoryFullName,

        @Schema(description = "이번 webhook으로 스캔한 브랜치 목록")
        List<String> scannedBranches,

        @Schema(description = "Webhook 처리 결과 메시지")
        String message
) {

    public static GithubWebhookResult ignored(String event, String repositoryFullName, String message) {
        return new GithubWebhookResult(event, repositoryFullName, List.of(), message);
    }
}
