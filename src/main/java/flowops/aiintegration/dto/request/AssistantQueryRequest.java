package flowops.aiintegration.dto.request;

public record AssistantQueryRequest(
        String question,
        String context
) {
}
