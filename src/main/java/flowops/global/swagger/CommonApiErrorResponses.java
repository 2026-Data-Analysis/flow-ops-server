package flowops.global.swagger;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 검증 실패",
                content = @Content(schema = @Schema(implementation = flowops.global.response.ApiResponse.class))),
        @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = flowops.global.response.ApiResponse.class))),
        @ApiResponse(responseCode = "405", description = "지원하지 않는 HTTP 메서드",
                content = @Content(schema = @Schema(implementation = flowops.global.response.ApiResponse.class))),
        @ApiResponse(responseCode = "409", description = "현재 상태에서 수행할 수 없는 요청 또는 중복 리소스",
                content = @Content(schema = @Schema(implementation = flowops.global.response.ApiResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                content = @Content(schema = @Schema(implementation = flowops.global.response.ApiResponse.class))),
        @ApiResponse(responseCode = "502", description = "외부 서비스 연동 실패",
                content = @Content(schema = @Schema(implementation = flowops.global.response.ApiResponse.class)))
})
public @interface CommonApiErrorResponses {
}
