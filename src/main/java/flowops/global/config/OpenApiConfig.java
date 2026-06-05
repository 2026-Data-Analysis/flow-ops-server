package flowops.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flowOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowOps API")
                        .version("v1")
                        .description("AI-first API QA platform backend API documentation.")
                        .contact(new Contact()
                                .name("FlowOps Backend")
                                .email("team@flowops.local")))
                .servers(List.of(new Server()
                        .url("/")
                        .description("Default server")));
    }

    @Bean
    public OpenApiCustomizer commonErrorResponsesOpenApiCustomizer() {
        Map<String, ApiResponse> commonErrorResponses = Map.of(
                "400", errorResponse("잘못된 요청 또는 검증 실패"),
                "404", errorResponse("리소스를 찾을 수 없음"),
                "405", errorResponse("지원하지 않는 HTTP 메서드"),
                "409", errorResponse("현재 상태에서 수행할 수 없는 요청 또는 중복 리소스"),
                "500", errorResponse("서버 내부 오류"),
                "502", errorResponse("외부 서비스 연동 실패")
        );
        return openApi -> openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
            ApiResponses responses = operation.getResponses();
            commonErrorResponses.forEach(responses::addApiResponse);
        }));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new io.swagger.v3.oas.models.media.Content()
                        .addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType()
                                .schema(apiResponseSchema())));
    }

    private Schema<?> apiResponseSchema() {
        return new ObjectSchema()
                .addProperty("success", new BooleanSchema().example(false))
                .addProperty("code", new StringSchema().example("COMMON-400"))
                .addProperty("message", new StringSchema().example("요청 값이 올바르지 않습니다."));
    }
}
