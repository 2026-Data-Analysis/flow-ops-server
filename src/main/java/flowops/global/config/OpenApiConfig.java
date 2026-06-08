package flowops.global.config;

import flowops.global.response.ApiResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

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
        Map<String, io.swagger.v3.oas.models.responses.ApiResponse> commonErrorResponses = Map.of(
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

    @Bean
    public OperationCustomizer successResponseOperationCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            io.swagger.v3.oas.models.responses.ApiResponse successResponse = responses.get("200");
            if (successResponse != null && successResponse.getContent() != null && !successResponse.getContent().isEmpty()) {
                return operation;
            }

            responses.addApiResponse("200", new io.swagger.v3.oas.models.responses.ApiResponse()
                    .description("정상 응답")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType()
                                    .schema(successResponseSchema(handlerMethod)))));
            return operation;
        };
    }

    private io.swagger.v3.oas.models.responses.ApiResponse errorResponse(String description) {
        return new io.swagger.v3.oas.models.responses.ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType("application/json", new MediaType()
                                .schema(errorResponseSchema())));
    }

    private Schema<?> errorResponseSchema() {
        return new ObjectSchema()
                .addProperty("success", new BooleanSchema().example(false))
                .addProperty("code", new StringSchema().example("COMMON-400"))
                .addProperty("message", new StringSchema().example("요청 값이 올바르지 않습니다."));
    }

    private Schema<?> successResponseSchema(HandlerMethod handlerMethod) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
        Class<?> rawReturnType = returnType.resolve();

        if (ResponseEntity.class.equals(rawReturnType)) {
            return schemaFromType(returnType.getGeneric(0));
        }
        if (ApiResponse.class.equals(rawReturnType)) {
            ResolvableType dataType = returnType.getGeneric(0);
            ObjectSchema schema = new ObjectSchema();
            schema.addProperty("success", new BooleanSchema().example(true));
            schema.addProperty("code", new StringSchema().example("COMMON-200"));
            schema.addProperty("message", new StringSchema().example("요청이 성공적으로 처리되었습니다."));
            if (!isVoidType(dataType)) {
                schema.addProperty("data", schemaFromType(dataType));
            }
            return schema;
        }
        return schemaFromType(returnType);
    }

    private boolean isVoidType(ResolvableType type) {
        Class<?> resolved = type.resolve();
        return resolved == null || Void.class.equals(resolved) || void.class.equals(resolved);
    }

    private Schema<?> schemaFromType(ResolvableType type) {
        if (isVoidType(type)) {
            return new ObjectSchema();
        }

        ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type.getType()).resolveAsRef(true));
        if (resolvedSchema == null || resolvedSchema.schema == null) {
            return new ObjectSchema();
        }
        return resolvedSchema.schema;
    }
}
