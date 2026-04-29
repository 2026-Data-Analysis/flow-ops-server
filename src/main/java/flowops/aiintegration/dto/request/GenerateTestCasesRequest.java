package flowops.aiintegration.dto.request;

public record GenerateTestCasesRequest(
        String apiSummary,
        String constraints
) {
}
