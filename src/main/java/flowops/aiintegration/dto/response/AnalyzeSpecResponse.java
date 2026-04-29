package flowops.aiintegration.dto.response;

public record AnalyzeSpecResponse(
        String summary,
        String recommendation,
        String modelName
) {
}
