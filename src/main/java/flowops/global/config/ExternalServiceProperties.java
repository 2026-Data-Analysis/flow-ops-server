package flowops.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external")
public record ExternalServiceProperties(
        Github github,
        Ai ai
) {

    public record Github(
            String apiUrl,
            String token,
            boolean mockEnabled
    ) {
    }

    public record Ai(
            String baseUrl,
            String apiKey,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
    }
}
