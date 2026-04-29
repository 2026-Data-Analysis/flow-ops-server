package flowops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * FlowOps 백엔드 애플리케이션의 시작점입니다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FlowOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowOpsApplication.class, args);
    }
}
