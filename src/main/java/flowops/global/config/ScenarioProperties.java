package flowops.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowops.scenario")
public record ScenarioProperties(
        boolean demoFallbackEnabled,
        int demoFallbackMaxRetries
) {
    public ScenarioProperties {
        if (demoFallbackMaxRetries <= 0) {
            demoFallbackMaxRetries = 2;
        }
    }
}
