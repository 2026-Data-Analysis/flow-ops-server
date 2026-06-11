package flowops.github.dto.response;

import flowops.apiinventory.dto.response.ScanResultResponse;
import flowops.github.domain.entity.RepositoryConnectionStatus;
import flowops.github.domain.entity.RepositoryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RepositoryResponse(
        @Schema(description = "Repository ID", example = "20")
        Long id,
        @Schema(description = "Project ID", example = "1")
        Long projectId,
        @Schema(description = "Linked app ID", example = "1")
        Long appId,
        @Schema(description = "GitHub repository full name", example = "flowops/backend")
        String fullName,
        @Schema(description = "Repository URL", example = "https://github.com/flowops/backend")
        String repositoryUrl,
        @Schema(description = "Default branch", example = "main")
        String defaultBranch,
        @Schema(description = "Repository connection status", example = "ACTIVE")
        RepositoryConnectionStatus connectionStatus,
        @Schema(description = "Whether Merge push webhooks automatically refresh API Inventory", example = "true")
        boolean autoSyncEnabled,
        @Schema(description = "Repository branches")
        List<BranchResponse> branches,
        @Schema(description = "API inventory scan results")
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
                repositoryInfo.getApp() == null ? null : repositoryInfo.getApp().getId(),
                repositoryInfo.getFullName(),
                repositoryInfo.getRepositoryUrl(),
                repositoryInfo.getDefaultBranch(),
                repositoryInfo.getConnectionStatus(),
                repositoryInfo.isAutoSyncEnabled(),
                branches,
                scanResults
        );
    }
}
