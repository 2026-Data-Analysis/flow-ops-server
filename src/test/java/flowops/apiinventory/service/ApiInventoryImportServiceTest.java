package flowops.apiinventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiInventoryImportServiceTest {

    private final ApiInventoryImportService service = new ApiInventoryImportService(null, null, null);

    @Test
    void detectsAuthenticationPrincipalAsAuthRequiredInSpringControllerScan() {
        var operations = service.parseSpringControllerOperations("""
                @RestController
                @RequestMapping("/members")
                class MemberController {

                    @GetMapping("/me")
                    public MemberResponse me(@AuthenticationPrincipal CustomUser user) {
                        return null;
                    }
                }
                """);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).endpointPath()).isEqualTo("/members/me");
        assertThat(operations.get(0).authRequired()).isTrue();
    }

    @Test
    void detectsAuthorizationHeaderAsAuthRequiredInSpringControllerScan() {
        var operations = service.parseSpringControllerOperations("""
                @RestController
                class ApiController {

                    @PostMapping("/payments")
                    public void pay(@RequestHeader("Authorization") String token) {
                    }
                }
                """);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).authRequired()).isTrue();
    }

    @Test
    void appliesClassLevelSecurityAnnotationToSpringControllerOperations() {
        var operations = service.parseSpringControllerOperations("""
                @RestController
                @PreAuthorize("isAuthenticated()")
                @RequestMapping("/admin")
                class AdminController {

                    @DeleteMapping("/users/{id}")
                    public void deleteUser() {
                    }
                }
                """);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).authRequired()).isTrue();
    }

    @Test
    void permitAllOverridesClassLevelSecurityAnnotation() {
        var operations = service.parseSpringControllerOperations("""
                @RestController
                @PreAuthorize("isAuthenticated()")
                @RequestMapping("/auth")
                class AuthController {

                    @PermitAll
                    @PostMapping("/login")
                    public void login() {
                    }
                }
                """);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).authRequired()).isFalse();
    }
}
