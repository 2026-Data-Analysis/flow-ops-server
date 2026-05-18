package flowops.apiinventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenApiSpecParserTest {

    private final OpenApiSpecParser parser = new OpenApiSpecParser(new ObjectMapper());

    @Test
    void fallsBackToEndpointPathWhenOperationTagsAreMissing() {
        ParsedOpenApiSpec spec = parser.parse("openapi.json", """
                {
                  "openapi": "3.0.1",
                  "paths": {
                    "/users/likes/{mateId}": {
                      "delete": {
                        "operationId": "unlike",
                        "responses": { "204": { "description": "No Content" } }
                      }
                    }
                  }
                }
                """);

        assertThat(spec.operations()).hasSize(1);
        assertThat(spec.operations().get(0).domainTag()).isEqualTo("USERS");
    }

    @Test
    void marksOperationAsAuthRequiredWhenGlobalSecurityRequirementExists() {
        ParsedOpenApiSpec spec = parser.parse("openapi.json", """
                {
                  "openapi": "3.0.1",
                  "security": [{ "bearerAuth": [] }],
                  "paths": {
                    "/members/me": {
                      "get": {
                        "responses": { "200": { "description": "OK" } }
                      }
                    }
                  }
                }
                """);

        assertThat(spec.operations()).hasSize(1);
        assertThat(spec.operations().get(0).authRequired()).isTrue();
    }

    @Test
    void treatsEmptyOperationSecurityRequirementAsPublicOverride() {
        ParsedOpenApiSpec spec = parser.parse("openapi.json", """
                {
                  "openapi": "3.0.1",
                  "security": [{ "bearerAuth": [] }],
                  "paths": {
                    "/auth/login": {
                      "post": {
                        "security": [{}],
                        "responses": { "200": { "description": "OK" } }
                      }
                    }
                  }
                }
                """);

        assertThat(spec.operations()).hasSize(1);
        assertThat(spec.operations().get(0).authRequired()).isFalse();
    }
}
