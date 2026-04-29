package flowops.global.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.IvGenerator;
import org.jasypt.iv.NoIvGenerator;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * prod 프로필에서 ENC(...) 설정값을 복호화하기 위한 Jasypt encryptor를 명시적으로 구성합니다.
 */
@Configuration
@ConditionalOnProperty(name = "jasypt.encryptor.password")
public class JasyptConfig {

    @Bean("jasyptStringEncryptor")
    public StringEncryptor jasyptStringEncryptor(
            @Value("${jasypt.encryptor.password}") String password,
            @Value("${jasypt.encryptor.algorithm:PBEWITHHMACSHA512ANDAES_256}") String algorithm,
            @Value("${jasypt.encryptor.key-obtention-iterations:1000}") int keyObtentionIterations,
            @Value("${jasypt.encryptor.iv-generator-classname:org.jasypt.iv.RandomIvGenerator}") String ivGeneratorClassName,
            @Value("${jasypt.encryptor.string-output-type:base64}") String stringOutputType
    ) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("jasypt.encryptor.password는 비어 있을 수 없습니다.");
        }

        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setAlgorithm(algorithm);
        encryptor.setKeyObtentionIterations(keyObtentionIterations);
        encryptor.setStringOutputType(stringOutputType);
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setIvGenerator(createIvGenerator(ivGeneratorClassName));
        return encryptor;
    }

    private IvGenerator createIvGenerator(String className) {
        return switch (className) {
            case "org.jasypt.iv.NoIvGenerator" -> new NoIvGenerator();
            case "org.jasypt.iv.RandomIvGenerator" -> new RandomIvGenerator();
            default -> throw new IllegalArgumentException("지원하지 않는 IV 생성기입니다: " + className);
        };
    }
}
