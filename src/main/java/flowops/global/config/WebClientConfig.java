package flowops.global.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 외부 서비스별 WebClient를 분리해 GitHub와 AI 서버 호출 설정이 섞이지 않도록 관리합니다.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("githubApiWebClient")
    public WebClient githubApiWebClient(WebClient.Builder builder, ExternalServiceProperties properties) {
        return builder
                .baseUrl(properties.github().apiUrl())
                .defaultHeaders(headers -> {
                    if (properties.github().token() != null && !properties.github().token().isBlank()) {
                        headers.setBearerAuth(properties.github().token());
                    }
                })
                .build();
    }

    @Bean
    @Qualifier("aiApiWebClient")
    public WebClient aiApiWebClient(WebClient.Builder builder, ExternalServiceProperties properties) {
        ExternalServiceProperties.Ai ai = properties.ai();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ai.connectTimeoutMillis())
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(ai.readTimeoutMillis(), TimeUnit.MILLISECONDS)
                ));

        return builder
                .baseUrl(ai.baseUrl())
                .defaultHeaders(headers -> {
                    if (ai.apiKey() != null && !ai.apiKey().isBlank()) {
                        headers.setBearerAuth(ai.apiKey());
                    }
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
