package flowops.environment.service;

import flowops.environment.domain.entity.AuthType;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretService {

    private static final String ENCRYPTED_PREFIX = "ENC:";

    private final ObjectProvider<StringEncryptor> encryptorProvider;

    public EnvironmentSecretService(ObjectProvider<StringEncryptor> encryptorProvider) {
        this.encryptorProvider = encryptorProvider;
    }

    public String protect(AuthType authType, String authConfig) {
        if (authConfig == null || authConfig.isBlank()) {
            return authConfig;
        }
        if (authConfig.startsWith(ENCRYPTED_PREFIX)) {
            return authConfig;
        }
        String normalized = normalize(authType, authConfig);
        StringEncryptor encryptor = encryptorProvider.getIfAvailable();
        if (encryptor == null) {
            return normalized;
        }
        return ENCRYPTED_PREFIX + encryptor.encrypt(normalized);
    }

    private String normalize(AuthType authType, String authConfig) {
        if (authType == AuthType.BEARER && !authConfig.trim().startsWith("{")) {
            return "{\"token\":\"" + escape(authConfig.trim()) + "\"}";
        }
        return authConfig;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
