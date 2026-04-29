package flowops.github.dto.response;

import flowops.apiinventory.dto.response.ScanResultResponse;
import flowops.github.domain.entity.RepositoryConnectionStatus;
import flowops.github.domain.entity.RepositoryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RepositoryResponse(
        @Schema(description = "저장소 ID", example = "20")
        Long id,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "전체 저장소 이름", example = "flowops/backend")
        String fullName,
        @Schema(description = "저장소 URL", example = "https://github.com/flowops/backend")
        String repositoryUrl,
        @Schema(description = "기본 브랜치", example = "main")
        String defaultBranch,
        @Schema(description = "연결 상태", example = "ACTIVE")
        RepositoryConnectionStatus connectionStatus,
        @Schema(description = "브랜치 목록")
        List<BranchResponse> branches,
        @Schema(description = "선택 브랜치별 API 스캔 결과")
        List<ScanResultResponse> scanResults
) {
    public static RepositoryResponse from(
            RepositoryInfo repositoryInfo,
            List<BranchResponse> branches,
            List<ScanResultResponse> scanResults
    ) {
        return new RepositoryResponse(
                repositoryInfo.getId(),
                repositoryInfo.getProject().getId(),
                repositoryInfo.getFullName(),
                repositoryInfo.getRepositoryUrl(),
                repositoryInfo.getDefaultBranch(),
                repositoryInfo.getConnectionStatus(),
                branches,
                scanResults
        );
    }
}
