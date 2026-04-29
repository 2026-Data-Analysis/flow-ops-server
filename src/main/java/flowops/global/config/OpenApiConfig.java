package flowops.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
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
}
