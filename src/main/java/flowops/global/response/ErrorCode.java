package flowops.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(HttpStatus.OK, "COMMON-200", "요청이 성공적으로 처리되었습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON-400", "요청 값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "요청한 리소스를 찾을 수 없습니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON-409", "이미 존재하는 리소스입니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "COMMON-410", "현재 상태에서는 수행할 수 없는 작업입니다."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "COMMON-502", "외부 서비스 요청에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
