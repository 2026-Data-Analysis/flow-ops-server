package flowops.apiinventory.service;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.domain.entity.ApiInventoryStatus;
import flowops.apiinventory.dto.response.ScanResultResponse;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.github.client.GithubClient;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.github.dto.response.RepositoryFile;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GitHub 브랜치에서 API 명세 파일을 가져와 API Inventory로 동기화하고 스캔 요약을 만듭니다.
 */
@Service
@RequiredArgsConstructor
public class ApiInventoryImportService {

    private static final Pattern GRADLE_SPRING_BOOT_PLUGIN = Pattern.compile(
            "org\\.springframework\\.boot['\"]?\\)?\\s+version\\s*[\"']([^\"']+)[\"']"
    );
    private static final Pattern MAVEN_SPRING_BOOT_PARENT = Pattern.compile(
            "<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>([^<]+)</version>",
            Pattern.DOTALL
    );
    private static final Pattern MAVEN_SPRING_BOOT_PROPERTY = Pattern.compile(
            "<spring-boot.version>([^<]+)</spring-boot.version>"
    );

    private final GithubClient githubClient;
    private final OpenApiSpecParser openApiSpecParser;
    private final ApiInventoryRepository apiInventoryRepository;

    @Transactional
    public ScanResultResponse importFromRepositoryBranch(RepositoryInfo repositoryInfo, String branchName) {
        String owner = repositoryInfo.getOwner();
        String repositoryName = repositoryInfo.getRepositoryName();
        List<RepositoryFile> repositoryFiles = githubClient.findRepositoryFiles(owner, repositoryName, branchName);
        List<RepositoryFile> specFiles = repositoryFiles.stream()
                .filter(file -> isOpenApiSpecPath(file.path()))
                .toList();

        apiInventoryRepository.deleteByRepositoryInfoIdAndBranchName(repositoryInfo.getId(), branchName);

        ScanSummary scanSummary = new ScanSummary();
        for (RepositoryFile specFile : specFiles) {
            githubClient.fetchFileContent(owner, repositoryName, specFile.path(), branchName)
                    .map(content -> saveParsedOperations(repositoryInfo, branchName, specFile, content, scanSummary))
                    .orElse(0);
        }

        FrameworkScanResult frameworkScanResult = scanFramework(owner, repositoryName, branchName, repositoryFiles);
        return new ScanResultResponse(
                branchName,
                scanSummary.detectedEndpointCount(),
                scanSummary.toolName(),
                scanSummary.toolVersion(),
                scanSummary.methodEndpointCounts(),
                frameworkScanResult.frameworkName(),
                frameworkScanResult.frameworkVersion(),
                frameworkScanResult.restControllerCount()
        );
    }

    private int saveParsedOperations(
            RepositoryInfo repositoryInfo,
            String branchName,
            RepositoryFile specFile,
            String content,
            ScanSummary scanSummary
    ) {
        ParsedOpenApiSpec parsedSpec = openApiSpecParser.parse(specFile.name(), content);
        List<ParsedApiOperation> operations = parsedSpec.operations();
        scanSummary.addSpec(parsedSpec);
        operations.stream()
                .map(operation -> ApiInventory.builder()
                        .project(repositoryInfo.getProject())
                        .repositoryInfo(repositoryInfo)
                        .method(operation.method())
                        .endpointPath(operation.endpointPath())
                        .operationId(operation.operationId())
                        .branchName(branchName)
                        .summary(operation.summary())
                        .sourceType(ApiInventorySource.OPENAPI)
                        .status(ApiInventoryStatus.ACTIVE)
                        .specVersion(operation.specVersion())
                        .authRequired(operation.authRequired())
                        .build())
                .forEach(apiInventoryRepository::save);
        return operations.size();
    }

    private FrameworkScanResult scanFramework(
            String owner,
            String repositoryName,
            String branchName,
            List<RepositoryFile> repositoryFiles
    ) {
        FrameworkScanResult frameworkScanResult = detectSpringBoot(owner, repositoryName, branchName, repositoryFiles);
        int controllerCount = countRestControllers(owner, repositoryName, branchName, repositoryFiles);
        return new FrameworkScanResult(
                frameworkScanResult.frameworkName(),
                frameworkScanResult.frameworkVersion(),
                controllerCount
        );
    }

    private FrameworkScanResult detectSpringBoot(
            String owner,
            String repositoryName,
            String branchName,
            List<RepositoryFile> repositoryFiles
    ) {
        Optional<RepositoryFile> buildFile = repositoryFiles.stream()
                .filter(file -> isBuildFile(file.path()))
                .findFirst();
        if (buildFile.isEmpty()) {
            return new FrameworkScanResult(null, null, 0);
        }

        return githubClient.fetchFileContent(owner, repositoryName, buildFile.get().path(), branchName)
                .map(content -> {
                    if (!content.contains("spring-boot")) {
                        return new FrameworkScanResult(null, null, 0);
                    }
                    return new FrameworkScanResult("Spring Boot", detectSpringBootVersion(content), 0);
                })
                .orElse(new FrameworkScanResult(null, null, 0));
    }

    private int countRestControllers(
            String owner,
            String repositoryName,
            String branchName,
            List<RepositoryFile> repositoryFiles
    ) {
        return (int) repositoryFiles.stream()
                .filter(file -> file.path().endsWith(".java"))
                .filter(file -> file.name().contains("Controller") || file.path().contains("/controller/"))
                .limit(200)
                .map(file -> githubClient.fetchFileContent(owner, repositoryName, file.path(), branchName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(content -> content.contains("@RestController"))
                .count();
    }

    private boolean isBuildFile(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.endsWith("build.gradle")
                || normalized.endsWith("build.gradle.kts")
                || normalized.endsWith("pom.xml");
    }

    private String detectSpringBootVersion(String content) {
        return firstGroup(content, GRADLE_SPRING_BOOT_PLUGIN)
                .or(() -> firstGroup(content, MAVEN_SPRING_BOOT_PARENT))
                .or(() -> firstGroup(content, MAVEN_SPRING_BOOT_PROPERTY))
                .orElse(null);
    }

    private Optional<String> firstGroup(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private boolean isOpenApiSpecPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
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

    private static class ScanSummary {

        private final Map<ApiHttpMethod, Integer> methodEndpointCounts = new EnumMap<>(ApiHttpMethod.class);
        private String toolName;
        private String toolVersion;
        private int detectedEndpointCount;

        private void addSpec(ParsedOpenApiSpec parsedSpec) {
            if (toolName == null) {
                toolName = parsedSpec.toolName();
            }
            if (toolVersion == null) {
                toolVersion = parsedSpec.toolVersion();
            }
            for (ParsedApiOperation operation : parsedSpec.operations()) {
                detectedEndpointCount++;
                methodEndpointCounts.merge(operation.method(), 1, Integer::sum);
            }
        }

        private Map<ApiHttpMethod, Integer> methodEndpointCounts() {
            return Map.copyOf(methodEndpointCounts);
        }

        private String toolName() {
            return toolName;
        }

        private String toolVersion() {
            return toolVersion;
        }

        private int detectedEndpointCount() {
            return detectedEndpointCount;
        }
    }

    private record FrameworkScanResult(String frameworkName, String frameworkVersion, Integer restControllerCount) {
    }
}
