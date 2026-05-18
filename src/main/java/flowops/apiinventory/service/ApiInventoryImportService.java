package flowops.apiinventory.service;

import flowops.apiinventory.domain.DomainTag;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final Pattern CLASS_DECLARATION = Pattern.compile(
            "\\b(?:class|record)\\s+\\w+"
    );
    private static final Pattern REQUEST_MAPPING = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\\s*(?:\\(([^)]*)\\))?"
    );
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?:public|protected|private)\\s+[\\w<>?,\\s\\[\\].]+\\s+(\\w+)\\s*\\("
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"|'([^']*)'");
    private static final Pattern REQUEST_METHOD = Pattern.compile("RequestMethod\\.([A-Z]+)");
    private static final Pattern CLASS_AUTH_REQUIRED = Pattern.compile(
            "@(?:PreAuthorize|PostAuthorize|Secured|RolesAllowed|SecurityRequirement|AuthenticationPrincipal)\\b"
    );
    private static final Pattern METHOD_AUTH_REQUIRED = Pattern.compile(
            "@(?:PreAuthorize|PostAuthorize|Secured|RolesAllowed|SecurityRequirement|AuthenticationPrincipal)\\b"
                    + "|\\b(?:Authentication|Jwt|JwtAuthenticationToken|OAuth2AuthenticationToken|Principal|SecurityContext)\\b"
                    + "|@RequestHeader\\s*\\([^)]*(?:Authorization|HttpHeaders\\.AUTHORIZATION)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PERMIT_ALL = Pattern.compile("@PermitAll\\b");

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
        if (scanSummary.detectedEndpointCount() == 0) {
            saveSpringControllerOperations(repositoryInfo, branchName, owner, repositoryName, repositoryFiles, scanSummary);
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
                        .domainTag(operation.domainTag())
                        .branchName(branchName)
                        .summary(operation.summary())
                        .sourceType(ApiInventorySource.OPENAPI)
                        .status(ApiInventoryStatus.ACTIVE)
                        .specVersion(operation.specVersion())
                        .authRequired(operation.authRequired())
                        .requestSchema(operation.requestSchema())
                        .responseSchema(operation.responseSchema())
                        .build())
                .forEach(apiInventoryRepository::save);
        return operations.size();
    }

    private void saveSpringControllerOperations(
            RepositoryInfo repositoryInfo,
            String branchName,
            String owner,
            String repositoryName,
            List<RepositoryFile> repositoryFiles,
            ScanSummary scanSummary
    ) {
        Set<String> seenOperations = new LinkedHashSet<>();
        repositoryFiles.stream()
                .filter(file -> file.path().endsWith(".java"))
                .filter(file -> file.name().contains("Controller") || file.path().contains("/controller/"))
                .limit(200)
                .forEach(file -> githubClient.fetchFileContent(owner, repositoryName, file.path(), branchName)
                        .filter(content -> content.contains("@RestController"))
                        .map(this::parseSpringControllerOperations)
                        .orElse(List.of())
                        .stream()
                        .filter(operation -> seenOperations.add(operation.method() + " " + operation.endpointPath()))
                        .map(operation -> ApiInventory.builder()
                                .project(repositoryInfo.getProject())
                                .repositoryInfo(repositoryInfo)
                                .method(operation.method())
                                .endpointPath(operation.endpointPath())
                                .operationId(operation.operationId())
                                .domainTag(operation.domainTag())
                                .branchName(branchName)
                                .summary(operation.summary())
                                .sourceType(ApiInventorySource.SPRING_CONTROLLER)
                                .status(ApiInventoryStatus.ACTIVE)
                                .specVersion(operation.specVersion())
                                .authRequired(operation.authRequired())
                                .build())
                        .forEach(apiInventory -> {
                            apiInventoryRepository.save(apiInventory);
                            scanSummary.addOperation(apiInventory.getMethod());
                        }));
    }

    List<ParsedApiOperation> parseSpringControllerOperations(String content) {
        String basePath = controllerBasePath(content);
        int classStart = classStart(content);
        boolean classAuthRequired = classAuthRequired(content);
        List<ParsedApiOperation> operations = new ArrayList<>();
        Matcher mappingMatcher = REQUEST_MAPPING.matcher(content);
        while (mappingMatcher.find()) {
            if (mappingMatcher.start() < classStart) {
                continue;
            }
            int afterAnnotation = mappingMatcher.end();
            Matcher methodMatcher = METHOD_DECLARATION.matcher(content);
            methodMatcher.region(afterAnnotation, Math.min(content.length(), afterAnnotation + 600));
            if (!methodMatcher.find()) {
                continue;
            }

            List<ApiHttpMethod> methods = mappingMethods(mappingMatcher.group(1), mappingMatcher.group(2));
            List<String> paths = mappingPaths(mappingMatcher.group(2));
            boolean authRequired = authRequired(content, annotationBlockStart(content, mappingMatcher.start()), methodMatcher.end(), classAuthRequired);
            for (ApiHttpMethod method : methods) {
                for (String path : paths) {
                    operations.add(new ParsedApiOperation(
                            method,
                            joinPaths(basePath, path),
                            methodMatcher.group(1),
                            inferDomainTag(joinPaths(basePath, path)),
                            null,
                            "Spring MVC",
                            authRequired,
                            "{}",
                            null
                    ));
                }
            }
        }
        return operations;
    }

    private int classStart(String content) {
        Matcher classMatcher = CLASS_DECLARATION.matcher(content);
        return classMatcher.find() ? classMatcher.start() : 0;
    }

    private boolean classAuthRequired(String content) {
        Matcher classMatcher = CLASS_DECLARATION.matcher(content);
        if (!classMatcher.find()) {
            return false;
        }
        String classPrefix = content.substring(0, classMatcher.start());
        return !PERMIT_ALL.matcher(classPrefix).find() && CLASS_AUTH_REQUIRED.matcher(classPrefix).find();
    }

    private boolean authRequired(String content, int mappingStart, int methodDeclarationEnd, boolean classAuthRequired) {
        String methodHeader = methodHeader(content, mappingStart, methodDeclarationEnd);
        if (PERMIT_ALL.matcher(methodHeader).find()) {
            return false;
        }
        return classAuthRequired || METHOD_AUTH_REQUIRED.matcher(methodHeader).find();
    }

    private String methodHeader(String content, int mappingStart, int methodDeclarationEnd) {
        int headerEnd = content.indexOf('{', methodDeclarationEnd);
        if (headerEnd < 0 || headerEnd - mappingStart > 1200) {
            headerEnd = Math.min(content.length(), methodDeclarationEnd + 600);
        }
        return content.substring(mappingStart, headerEnd);
    }

    private int annotationBlockStart(String content, int annotationStart) {
        int blockStart = lineStart(content, annotationStart);
        int cursor = blockStart;
        while (cursor > 0) {
            int previousLineEnd = cursor - 1;
            int previousLineStart = lineStart(content, previousLineEnd);
            String previousLine = content.substring(previousLineStart, previousLineEnd).trim();
            if (!previousLine.isEmpty() && !previousLine.startsWith("@")) {
                break;
            }
            blockStart = previousLineStart;
            cursor = previousLineStart;
        }
        return blockStart;
    }

    private int lineStart(String content, int index) {
        int newline = content.lastIndexOf('\n', Math.max(0, index - 1));
        return newline < 0 ? 0 : newline + 1;
    }

    private String controllerBasePath(String content) {
        Matcher classMatcher = CLASS_DECLARATION.matcher(content);
        if (!classMatcher.find()) {
            return "";
        }

        String classPrefix = content.substring(0, classMatcher.start());
        Matcher mappingMatcher = REQUEST_MAPPING.matcher(classPrefix);
        String basePath = "";
        while (mappingMatcher.find()) {
            if ("RequestMapping".equals(mappingMatcher.group(1))) {
                basePath = mappingPaths(mappingMatcher.group(2)).get(0);
            }
        }
        return basePath;
    }

    private List<ApiHttpMethod> mappingMethods(String annotationName, String annotationArgs) {
        return switch (annotationName) {
            case "GetMapping" -> List.of(ApiHttpMethod.GET);
            case "PostMapping" -> List.of(ApiHttpMethod.POST);
            case "PutMapping" -> List.of(ApiHttpMethod.PUT);
            case "PatchMapping" -> List.of(ApiHttpMethod.PATCH);
            case "DeleteMapping" -> List.of(ApiHttpMethod.DELETE);
            default -> requestMappingMethods(annotationArgs);
        };
    }

    private List<ApiHttpMethod> requestMappingMethods(String annotationArgs) {
        if (annotationArgs == null || annotationArgs.isBlank()) {
            return List.of(ApiHttpMethod.GET);
        }

        List<ApiHttpMethod> methods = new ArrayList<>();
        Matcher matcher = REQUEST_METHOD.matcher(annotationArgs);
        while (matcher.find()) {
            try {
                methods.add(ApiHttpMethod.valueOf(matcher.group(1)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return methods.isEmpty() ? List.of(ApiHttpMethod.GET) : methods;
    }

    private List<String> mappingPaths(String annotationArgs) {
        if (annotationArgs == null || annotationArgs.isBlank()) {
            return List.of("");
        }

        List<String> paths = new ArrayList<>();
        Matcher matcher = STRING_LITERAL.matcher(annotationArgs);
        while (matcher.find()) {
            paths.add(Optional.ofNullable(matcher.group(1)).orElse(matcher.group(2)));
        }
        return paths.isEmpty() ? List.of("") : paths;
    }

    private String joinPaths(String basePath, String path) {
        String normalizedBase = normalizePath(basePath);
        String normalizedPath = normalizePath(path);
        if (normalizedBase.equals("/")) {
            return normalizedPath;
        }
        if (normalizedPath.equals("/")) {
            return normalizedBase;
        }
        return normalizedBase + normalizedPath;
    }

    private String inferDomainTag(String path) {
        return DomainTag.fromPath(path);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
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

        private void addOperation(ApiHttpMethod method) {
            detectedEndpointCount++;
            methodEndpointCounts.merge(method, 1, Integer::sum);
            if (toolName == null) {
                toolName = "Spring MVC";
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
