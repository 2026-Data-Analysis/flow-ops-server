package flowops.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
