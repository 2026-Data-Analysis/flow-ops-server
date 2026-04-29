package flowops.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.encrypted-datasource")
public record EncryptedDatasourceProperties(
        String url,
        String username,
        String password,
        String driverClassName,
        Hikari hikari
) {

    public record Hikari(
            int maximumPoolSize,
            int minimumIdle,
            long connectionTimeout,
            long validationTimeout
    ) {
    }
}
