package flowops.apiinventory.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.ExistingScenarioSummary;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.scenario.dto.response.ScenarioSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "에이전트용 API 인벤토리 조회 응답")
public record AgentApiInventorySearchResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "앱 ID", example = "10")
        Long appId,

        @Schema(description = "저장소 ID", example = "20")
        Long repositoryId,

        @Schema(description = "조회한 브랜치명", example = "main")
        String branchName,

        @Schema(description = "검색 키워드", example = "주문")
        String keyword,

        @Schema(description = "필터 조건에 매칭된 전체 API 수", example = "42")
        int totalMatches,

        @Schema(description = "이번 응답에 포함된 API 수", example = "20")
        int count,

        @Schema(description = "테스트케이스 생성 에이전트 호출에 사용할 API 명세 목록")
        List<AgentApiSpec> apis,

        @Schema(description = "응답에 포함된 활성 테스트케이스 수", example = "12")
        int testCaseCount,

        @Schema(description = "테스트케이스 생성 에이전트 호출에 참고할 기존 테스트케이스 목록")
        List<AgentTestCaseSpec> testCases,

        @Schema(description = "저장된 시나리오 수", example = "4")
        int scenarioCount,

        @Schema(description = "저장된 시나리오 목록")
        List<ScenarioSummaryResponse> scenarios,

        @Schema(description = "NATURAL_LANGUAGE 모드 시나리오 생성 실행에 바로 사용할 수 있는 요청 필드")
        AgentNaturalLanguageScenarioRequest naturalLanguageScenarioRequest
) {

    @Schema(description = "에이전트용 API 명세")
    public record AgentApiSpec(
            @Schema(description = "테스트케이스 생성 에이전트에 전달할 API ID", example = "100")
            String apiId,

            @Schema(description = "API 인벤토리 ID", example = "100")
            Long inventoryId,

            @Schema(description = "저장소 ID", example = "20")
            Long repositoryId,

            @Schema(description = "HTTP 메서드", example = "GET")
            String method,

            @Schema(description = "API 엔드포인트 경로", example = "/orders/{orderId}")
            String path,

            @Schema(description = "도메인 태그", example = "ORDER")
            String domainTag,

            @Schema(description = "OpenAPI operationId", example = "getOrder")
            String operationId,

            @Schema(description = "API 요약", example = "주문 상세 조회")
            String summary,

            @Schema(description = "브랜치명", example = "main")
            String branchName,

            @Schema(description = "요청 파라미터, 헤더, body 스키마")
            JsonNode requestSchema,

            @Schema(description = "응답 스키마")
            JsonNode responseSchema,

            @Schema(description = "성공 응답으로 기대할 수 있는 HTTP 상태 코드 목록", example = "[200,201]")
            List<Integer> expectedStatusCodes,

            @Schema(description = "오류 응답으로 발생할 수 있는 HTTP 상태 코드 목록", example = "[400,401,500]")
            List<Integer> errorStatusCodes,

            @Schema(description = "응답 예시에서 추출한 오류 코드 목록", example = "[\"COMMON-400\",\"ORDER-404\"]")
            List<String> errorCodes,

            @Schema(description = "인증 필요 여부", example = "true")
            Boolean authRequired,

            @Schema(description = "Deprecated API 여부", example = "false")
            Boolean deprecated
    ) {
    }

    @Schema(description = "에이전트용 기존 테스트케이스 정보")
    public record AgentTestCaseSpec(
            @Schema(description = "테스트케이스 ID", example = "501")
            String testCaseId,

            @Schema(description = "테스트케이스가 연결된 API ID", example = "100")
            String apiId,

            @Schema(description = "테스트케이스 이름", example = "주문 상세 조회 성공")
            String name,

            @Schema(description = "테스트케이스 설명", example = "주문 ID로 주문 상세 정보를 정상 조회한다.")
            String description,

            @Schema(description = "테스트케이스 유형", example = "HAPPY_PATH")
            String type,

            @Schema(description = "테스트 레벨", example = "SMOKE")
            String testLevel,

            @Schema(description = "사용자 역할", example = "CUSTOMER")
            String userRole,

            @Schema(description = "상태 조건")
            String stateCondition,

            @Schema(description = "데이터 변형 조건")
            String dataVariant,

            @Schema(description = "요청 명세")
            JsonNode requestSpec,

            @Schema(description = "기대 결과 명세")
            JsonNode expectedSpec,

            @Schema(description = "검증 명세")
            JsonNode assertionSpec
    ) {
    }

    @Schema(description = "NATURAL_LANGUAGE 모드 시나리오 생성 요청")
    public record AgentNaturalLanguageScenarioRequest(
            String mode,
            @JsonProperty("user_intent")
            String user_intent,
            @JsonProperty("api_inventory")
            ScenarioApiInventoryPayload api_inventory,
            EnvironmentPayload environment,
            @JsonProperty("existing_test_cases")
            List<ScenarioExistingTestCasePayload> existing_test_cases,
            @JsonProperty("existing_scenarios")
            List<ExistingScenarioSummary> existing_scenarios,
            @JsonProperty("max_scenarios")
            Integer max_scenarios,
            @JsonProperty("max_steps_per_scenario")
            Integer max_steps_per_scenario
    ) {
    }
}
