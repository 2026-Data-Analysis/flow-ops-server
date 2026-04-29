package flowops.github.client;

import flowops.github.dto.response.BranchResponse;
import flowops.github.dto.response.RepositoryFile;
import flowops.github.dto.response.RepositorySnapshot;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * GitHub REST API 호출을 담당하며, 저장소/브랜치/명세 파일 조회를 캡슐화합니다.
 */
@Component
public class GithubApiClient implements GithubClient {

    private final WebClient githubWebClient;
    private final ExternalServiceProperties properties;

    public GithubApiClient(
            @Qualifier("githubApiWebClient") WebClient githubWebClient,
            ExternalServiceProperties properties
    ) {
        this.githubWebClient = githubWebClient;
        this.properties = properties;
    }

    @Override
    public RepositorySnapshot fetchRepository(String owner, String repositoryName) {
        if (properties.github().mockEnabled()) {
            return new RepositorySnapshot(
                    owner + "-" + repositoryName,
                    owner + "/" + repositoryName,
                    "https://github.com/" + owner + "/" + repositoryName,
                    "main"
            );
        }

        return githubWebClient.get()
                .uri("/repos/{owner}/{repo}", owner, repositoryName)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("GitHub 저장소 조회에 실패했습니다.")
                        .map(body -> new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, body)))
                .bodyToMono(GithubRepositoryPayload.class)
                .map(payload -> new RepositorySnapshot(
                        String.valueOf(payload.id()),
                        payload.fullName(),
                        payload.htmlUrl(),
                        payload.defaultBranch()
                ))
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                        : new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "GitHub 저장소 조회에 실패했습니다."))
                .block();
    }

    @Override
    public List<BranchResponse> fetchBranches(String owner, String repositoryName, String defaultBranch) {
        if (properties.github().mockEnabled()) {
            return List.of(new BranchResponse(defaultBranch, true, false), new BranchResponse("develop", false, false));
        }

        List<GithubBranchPayload> branches = githubWebClient.get()
                .uri("/repos/{owner}/{repo}/branches", owner, repositoryName)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("GitHub 브랜치 조회에 실패했습니다.")
                        .map(body -> new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, body)))
                .bodyToFlux(GithubBranchPayload.class)
                .collectList()
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(ex -> ex instanceof ApiException
                        ? Mono.error(ex)
                        : Mono.error(new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "GitHub 브랜치 조회에 실패했습니다.")))
                .block();

        return branches.stream()
                .map(branch -> new BranchResponse(branch.name(), branch.name().equals(defaultBranch), false))
                .toList();
    }

    @Override
    public List<RepositoryFile> findRepositoryFiles(String owner, String repositoryName, String branchName) {
        if (properties.github().mockEnabled()) {
            return List.of();
        }

        GithubTreePayload payload = fetchRepositoryTree(owner, repositoryName, branchName);
        if (payload == null || payload.tree() == null) {
            return List.of();
        }

        return payload.tree().stream()
                .filter(item -> "blob".equals(item.type()))
                .map(item -> new RepositoryFile(item.path(), fileName(item.path())))
                .toList();
    }

    @Override
    public List<RepositoryFile> findOpenApiSpecFiles(String owner, String repositoryName, String branchName) {
        if (properties.github().mockEnabled()) {
            return List.of();
        }

        GithubTreePayload payload = fetchRepositoryTree(owner, repositoryName, branchName);
        if (payload == null || payload.tree() == null) {
            return List.of();
        }

        // 파일명과 경로에 openapi/swagger/api-docs 힌트가 있는 JSON/YAML 파일만 명세 후보로 봅니다.
        return payload.tree().stream()
                .filter(item -> "blob".equals(item.type()))
                .filter(item -> isOpenApiSpecPath(item.path()))
                .map(item -> new RepositoryFile(item.path(), fileName(item.path())))
                .toList();
    }

    private GithubTreePayload fetchRepositoryTree(String owner, String repositoryName, String branchName) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/git/trees/{branch}")
                        .queryParam("recursive", "1")
                        .build(owner, repositoryName, branchName))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("GitHub 파일 트리 조회에 실패했습니다.")
                        .map(body -> new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, body)))
                .bodyToMono(GithubTreePayload.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                        : new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "GitHub 파일 트리 조회에 실패했습니다."))
                .block();
    }

    @Override
    public Optional<String> fetchFileContent(String owner, String repositoryName, String path, String branchName) {
        if (properties.github().mockEnabled()) {
            return Optional.empty();
        }

        GithubContentPayload payload = githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/contents/")
                        .path(path)
                        .queryParam("ref", branchName)
                        .build(owner, repositoryName))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("GitHub 파일 조회에 실패했습니다.")
                        .map(body -> new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, body)))
                .bodyToMono(GithubContentPayload.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                        : new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "GitHub 파일 조회에 실패했습니다."))
                .block();

        if (payload == null || payload.content() == null) {
            return Optional.empty();
        }

        String normalized = payload.content().replaceAll("\\s", "");
        return Optional.of(new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8));
    }

    private boolean isOpenApiSpecPath(String path) {
        String normalized = path.toLowerCase();
        boolean supportedExtension = normalized.endsWith(".json")
                || normalized.endsWith(".yaml")
                || normalized.endsWith(".yml");
        return supportedExtension
                && (normalized.contains("openapi")
                || normalized.contains("swagger")
                || normalized.endsWith("api-docs.json")
                || normalized.endsWith("api-docs.yaml")
                || normalized.endsWith("api-docs.yml"));
    }

    private String fileName(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private record GithubRepositoryPayload(Long id, String fullName, String htmlUrl, String defaultBranch) {
    }

    private record GithubBranchPayload(String name) {
    }

    private record GithubTreePayload(List<GithubTreeItemPayload> tree) {
    }

    private record GithubTreeItemPayload(String path, String type) {
    }

    private record GithubContentPayload(String content) {
    }
}
