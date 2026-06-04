package flowops.apiinventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

    @Test
    void preservesExpectedAndErrorStatusesFromOpenApiResponses() throws Exception {
        ParsedOpenApiSpec spec = parser.parse("openapi.json", """
                {
                  "openapi": "3.0.1",
                  "paths": {
                    "/orders": {
                      "post": {
                        "responses": {
                          "201": {
                            "description": "Created",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "id": { "type": "integer" },
                                    "status": { "type": "string", "example": "created" }
                                  }
                                }
                              }
                            }
                          },
                          "400": {
                            "description": "Invalid request",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "message": { "type": "string" }
                                  }
                                }
                              }
                            }
                          },
                          "500": { "description": "Internal server error" }
                        }
                      }
                    }
                  }
                }
                """);

        JsonNode responseSchema = new ObjectMapper().readTree(spec.operations().get(0).responseSchema());

        assertThat(responseSchema.path("expectedStatusCodes").get(0).asInt()).isEqualTo(201);
        assertThat(responseSchema.path("errorStatusCodes").get(0).asInt()).isEqualTo(400);
        assertThat(responseSchema.path("errorStatusCodes").get(1).asInt()).isEqualTo(500);
        assertThat(responseSchema.path("responses").size()).isEqualTo(3);
        assertThat(responseSchema.path("responses").get(0).path("sampleBody").path("status").asText()).isEqualTo("created");
        assertThat(responseSchema.path("responses").get(1).path("category").asText()).isEqualTo("ERROR");
    }
}
