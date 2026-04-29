package flowops.environment.dto.response;

import flowops.environment.domain.entity.AuthType;
import flowops.environment.domain.entity.Environment;
import flowops.environment.domain.entity.TestLevelSource;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record EnvironmentResponse(
        @Schema(description = "환경 ID", example = "3")
        Long id,
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "저장소 ID", example = "20")
        Long repositoryId,
        @Schema(description = "환경 이름", example = "main")
        String name,
        @Schema(description = "연결 브랜치명", example = "main")
        String branchName,
        @Schema(description = "기본 URL", example = "https://staging-api.flowops.dev")
        String baseUrl,
        @Schema(description = "인증 방식", example = "BEARER")
        AuthType authType,
        @Schema(description = "인증 설정 등록 여부", example = "true")
        boolean authConfigured,
        @Schema(description = "마스킹된 인증 설정", example = "********")
        String authConfig,
        @Schema(description = "기본 헤더 JSON", example = "{\"X-Tenant-Id\":\"flowops\"}")
        String headers,
        @Schema(description = "기본 테스트 위계", example = "REGRESSION")
        TestLevel defaultTestLevel,
        @Schema(description = "기본 테스트 위계 결정 출처", example = "AI_RECOMMENDED")
        TestLevelSource defaultTestLevelSource,
        @Schema(description = "최근 실행 시각", example = "2026-04-12T01:00:00")
        LocalDateTime lastRunAt,
        @Schema(description = "환경 기준 평균 커버리지", example = "64.5")
        double coverage,
        @Schema(description = "생성 일시", example = "2026-04-12T01:00:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-04-12T01:10:00")
        LocalDateTime updatedAt
) {

    public static EnvironmentResponse from(Environment environment, LocalDateTime lastRunAt, double coverage) {
        boolean authConfigured = environment.getAuthConfig() != null && !environment.getAuthConfig().isBlank();
        return new EnvironmentResponse(
                environment.getId(),
                environment.getApp().getId(),
                environment.getRepositoryInfo() == null ? null : environment.getRepositoryInfo().getId(),
                environment.getName(),
                environment.getBranchName(),
                environment.getBaseUrl(),
                environment.getAuthType(),
                authConfigured,
                authConfigured ? "********" : null,
                environment.getHeaders(),
                environment.getDefaultTestLevel(),
                environment.getDefaultTestLevelSource(),
                lastRunAt,
                coverage,
                environment.getCreatedAt(),
                environment.getUpdatedAt()
        );
    }
}
