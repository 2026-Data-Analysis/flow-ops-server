package flowops.global.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 복호화 문제를 진단할 때만 켜는 디버그용 컴포넌트입니다.
 */
@Component
@Profile("jasypt-debug")
public class JasyptDiagnostics implements InitializingBean {

    private final StringEncryptor encryptor;
    private final String datasourcePassword;
    private final String encryptorPassword;

    public JasyptDiagnostics(
            StringEncryptor encryptor,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${jasypt.encryptor.password:}") String encryptorPassword
    ) {
        this.encryptor = encryptor;
        this.datasourcePassword = datasourcePassword;
        this.encryptorPassword = encryptorPassword;
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println("[jasypt-debug] encryptor password length=" + encryptorPassword.length());
        if (datasourcePassword.startsWith("ENC(") && datasourcePassword.endsWith(")")) {
            String encrypted = datasourcePassword.substring(4, datasourcePassword.length() - 1);
            String decrypted = encryptor.decrypt(encrypted);
            System.out.println("[jasypt-debug] datasource password decrypted length=" + decrypted.length());
        } else {
            System.out.println("[jasypt-debug] datasource password is already plain, length=" + datasourcePassword.length());
        }
    }
}
