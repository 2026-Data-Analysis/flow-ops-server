package flowops.environment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.repository.ApiEndpointRepository;
import flowops.environment.domain.entity.TriggerRule;
import flowops.environment.domain.entity.TriggerScopeType;
import flowops.execution.dto.request.RunApisExecutionRequest;
import flowops.execution.service.RunTestService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 트리거 규칙을 실제 테스트 실행 요청으로 변환합니다.
 */
@Service
@RequiredArgsConstructor
public class TriggerExecutionService {

    private final ApiEndpointRepository apiEndpointRepository;
    private final RunTestService runTestService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void executeScheduledTrigger(TriggerRule rule) {
        List<Long> apiIds = resolveApiIds(rule);
        if (apiIds.isEmpty()) {
            return;
        }

        runTestService.runApis(new RunApisExecutionRequest(
                rule.getEnvironment().getApp().getId(),
                rule.getEnvironment().getId(),
                apiIds,
                rule.getExecutionMode(),
                null,
                "trigger:schedule:" + rule.getId()
        ));
        rule.markTriggered(LocalDateTime.now());
    }

    List<Long> resolveApiIds(TriggerRule rule) {
        List<ApiEndpoint> appApis = apiEndpointRepository.findByAppId(rule.getEnvironment().getApp().getId());
        if (rule.getScopeType() == TriggerScopeType.ALL_APIS) {
            return appApis.stream().map(ApiEndpoint::getId).toList();
        }
        if (rule.getScopeType() == TriggerScopeType.SELECTED_APIS) {
            List<Long> selectedIds = parseLongList(rule.getScopeValue());
            return appApis.stream()
                    .map(ApiEndpoint::getId)
                    .filter(selectedIds::contains)
                    .toList();
        }
        List<String> tags = parseStringList(rule.getScopeValue()).stream()
                .map(tag -> tag.toUpperCase(Locale.ROOT))
                .toList();
        return appApis.stream()
                .filter(api -> api.getDomainTag() != null && tags.contains(api.getDomainTag().toUpperCase(Locale.ROOT)))
                .map(ApiEndpoint::getId)
                .toList();
    }

    String resolveCronExpression(TriggerRule rule) {
        if (rule.getTriggerConfig() == null || rule.getTriggerConfig().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rule.getTriggerConfig());
            JsonNode cronNode = node.get("cron");
            return cronNode == null || cronNode.isNull() ? null : cronNode.asText();
        } catch (Exception ignored) {
            return rule.getTriggerConfig().trim();
        }
    }

    private List<Long> parseLongList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<Long>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
