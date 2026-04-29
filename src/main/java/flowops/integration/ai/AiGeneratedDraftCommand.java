package flowops.integration.ai;

public record AiGeneratedDraftCommand(
        Long apiId,
        String title,
        String description,
        String type,
        String userRole,
        String stateCondition,
        String dataVariant,
        String requestSpec,
        String expectedSpec,
        String assertionSpec,
        boolean duplicate
) {
}
