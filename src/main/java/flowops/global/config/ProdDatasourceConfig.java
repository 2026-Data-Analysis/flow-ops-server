package flowops.global.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
public class ProdDatasourceConfig {

    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    @Bean
    @Primary
    public DataSource dataSource(
            EncryptedDatasourceProperties properties,
            StringEncryptor stringEncryptor
    ) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(decryptIfNeeded(properties.url(), stringEncryptor));
        dataSource.setUsername(decryptIfNeeded(properties.username(), stringEncryptor));
        dataSource.setPassword(decryptIfNeeded(properties.password(), stringEncryptor));
        dataSource.setDriverClassName(properties.driverClassName());

        if (properties.hikari() != null) {
            dataSource.setMaximumPoolSize(properties.hikari().maximumPoolSize());
            dataSource.setMinimumIdle(properties.hikari().minimumIdle());
            dataSource.setConnectionTimeout(properties.hikari().connectionTimeout());
            dataSource.setValidationTimeout(properties.hikari().validationTimeout());
        }
        return dataSource;
    }

    private String decryptIfNeeded(String value, StringEncryptor stringEncryptor) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX)) {
            String encrypted = value.substring(ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length());
            return stringEncryptor.decrypt(encrypted);
        }
        return value;
    }
}
