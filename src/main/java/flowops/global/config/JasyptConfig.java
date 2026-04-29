package flowops.global.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.IvGenerator;
import org.jasypt.iv.NoIvGenerator;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.jasypt.salt.SaltGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "jasypt.encryptor.password")
public class JasyptConfig {

    @Bean
    public StringEncryptor stringEncryptor(
            @Value("${jasypt.encryptor.password}") String password,
            @Value("${jasypt.encryptor.algorithm:PBEWITHHMACSHA512ANDAES_256}") String algorithm,
            @Value("${jasypt.encryptor.key-obtention-iterations:1000}") int iterations,
            @Value("${jasypt.encryptor.salt-generator-classname:org.jasypt.salt.RandomSaltGenerator}") String saltGeneratorClassName,
            @Value("${jasypt.encryptor.iv-generator-classname:org.jasypt.iv.RandomIvGenerator}") String ivGeneratorClassName,
            @Value("${jasypt.encryptor.string-output-type:base64}") String outputType
    ) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setAlgorithm(algorithm);
        encryptor.setKeyObtentionIterations(iterations);
        encryptor.setStringOutputType(outputType);
        encryptor.setSaltGenerator(createSaltGenerator(saltGeneratorClassName));
        encryptor.setIvGenerator(createIvGenerator(ivGeneratorClassName));
        return encryptor;
    }

    private SaltGenerator createSaltGenerator(String className) {
        return switch (className) {
            case "org.jasypt.salt.RandomSaltGenerator" -> new RandomSaltGenerator();
            default -> throw new IllegalArgumentException("지원하지 않는 Salt 생성기입니다: " + className);
        };
    }

    private IvGenerator createIvGenerator(String className) {
        return switch (className) {
            case "org.jasypt.iv.NoIvGenerator" -> new NoIvGenerator();
            case "org.jasypt.iv.RandomIvGenerator" -> new RandomIvGenerator();
            default -> throw new IllegalArgumentException("지원하지 않는 IV 생성기입니다: " + className);
        };
    }
}
