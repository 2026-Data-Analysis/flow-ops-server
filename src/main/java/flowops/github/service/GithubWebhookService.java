package flowops.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.apiinventory.service.ApiInventoryImportService;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.github.dto.response.GithubWebhookResult;
import flowops.github.repository.RepositoryInfoRepository;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GithubWebhookService {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final Set<String> DEFAULT_INVENTORY_BRANCHES = Set.of("dev", "staging", "main");

    private final ObjectMapper objectMapper;
    private final ExternalServiceProperties properties;
    private final RepositoryInfoRepository repositoryInfoRepository;
    private final ApiInventoryImportService apiInventoryImportService;

    @Transactional
    public GithubWebhookResult handle(String event, String signature, String payload) {
        verifySignature(signature, payload);

        JsonNode root = readPayload(payload);
        String repositoryFullName = root.path("repository").path("full_name").asText(null);
        Optional<String> targetBranch = targetBranch(event, root);
        if (targetBranch.isEmpty()) {
            return GithubWebhookResult.ignored(event, repositoryFullName, "머지 대상 브랜치를 찾을 수 없어 API Inventory 갱신을 건너뛰었습니다.");
        }
        if (!inventoryBranches().contains(targetBranch.get())) {
            return GithubWebhookResult.ignored(event, repositoryFullName, "API Inventory 자동 갱신 대상 브랜치가 아니어서 건너뛰었습니다.");
        }
        if (repositoryFullName == null || repositoryFullName.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "GitHub webhook payload에 repository.full_name 값이 필요합니다.");
        }

        RepositoryInfo repositoryInfo = repositoryInfoRepository.findByFullName(repositoryFullName)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "등록된 GitHub 저장소를 찾을 수 없습니다."));

        apiInventoryImportService.importFromRepositoryBranch(repositoryInfo, targetBranch.get());
        return new GithubWebhookResult(
                event,
                repositoryFullName,
                List.of(targetBranch.get()),
                "API Inventory를 갱신했습니다."
        );
    }

    private Optional<String> targetBranch(String event, JsonNode root) {
        if ("pull_request".equals(event)) {
            JsonNode pullRequest = root.path("pull_request");
            boolean merged = pullRequest.path("merged").asBoolean(false);
            String action = root.path("action").asText("");
            if (!merged || !"closed".equals(action)) {
                return Optional.empty();
            }
            return text(pullRequest.path("base").path("ref"));
        }
        if ("push".equals(event) && isMergePush(root)) {
            return text(root.path("ref"))
                    .filter(ref -> ref.startsWith("refs/heads/"))
                    .map(ref -> ref.substring("refs/heads/".length()));
        }
        return Optional.empty();
    }

    private boolean isMergePush(JsonNode root) {
        String message = root.path("head_commit").path("message").asText("");
        return message.startsWith("Merge pull request")
                || message.startsWith("Merge branch")
                || message.startsWith("Merged ");
    }

    private Optional<String> text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        String value = node.asText();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private Set<String> inventoryBranches() {
        String configuredBranches = properties.github().inventoryWebhookBranches();
        if (configuredBranches == null || configuredBranches.isBlank()) {
            return DEFAULT_INVENTORY_BRANCHES;
        }
        Set<String> branches = new LinkedHashSet<>();
        Arrays.stream(configuredBranches.split(","))
                .map(String::trim)
                .filter(branch -> !branch.isBlank())
                .forEach(branches::add);
        return branches.isEmpty() ? DEFAULT_INVENTORY_BRANCHES : branches;
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "GitHub webhook payload 형식이 올바르지 않습니다.");
        }
    }

    private void verifySignature(String signature, String payload) {
        String secret = properties.github().webhookSecret();
        if (secret == null || secret.isBlank()) {
            return;
        }
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "GitHub webhook 서명이 누락되었습니다.");
        }
        String expectedSignature = SIGNATURE_PREFIX + hmacSha256(secret, payload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "GitHub webhook 서명이 올바르지 않습니다.");
        }
    }

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "GitHub webhook 서명 검증 중 오류가 발생했습니다.");
        }
    }
}
