package flowops.global.controller;

import flowops.global.response.ApiResponse;
import flowops.global.swagger.CommonApiErrorResponses;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CommonApiErrorResponses
@RestController
public class RootController {

    @GetMapping("/")
    public ApiResponse<Map<String, String>> root() {
        return ApiResponse.success(Map.of(
                "service", "flowops",
                "status", "ok"
        ));
    }
}
