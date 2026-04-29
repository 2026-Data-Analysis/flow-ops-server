package flowops.api.domain.entity;

import flowops.app.domain.entity.App;
import flowops.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Getter
@Entity
@Table(name = "api_endpoints")
@Comment("애플리케이션에서 테스트 대상으로 관리되는 API 엔드포인트")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiEndpoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("API 엔드포인트 식별자")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    @Comment("API가 속한 앱")
    private App app;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Comment("HTTP 메서드")
    private ApiMethod method;

    @Column(nullable = false, length = 500)
    @Comment("API 경로")
    private String path;

    @Column(name = "domain_tag", length = 100)
    @Comment("도메인 태그")
    private String domainTag;

    @Column(name = "controller_name", length = 150)
    @Comment("컨트롤러 이름")
    private String controllerName;

    @Column(name = "request_schema", columnDefinition = "TEXT")
    @Comment("요청 스키마")
    private String requestSchema;

    @Column(name = "response_schema", columnDefinition = "TEXT")
    @Comment("응답 스키마")
    private String responseSchema;

    @Column(name = "is_deprecated", nullable = false)
    @Comment("Deprecated 여부")
    private boolean deprecated;

    @Builder
    private ApiEndpoint(
            App app,
            ApiMethod method,
            String path,
            String domainTag,
            String controllerName,
            String requestSchema,
            String responseSchema,
            boolean deprecated
    ) {
        this.app = app;
        this.method = method;
        this.path = path;
        this.domainTag = domainTag;
        this.controllerName = controllerName;
        this.requestSchema = requestSchema;
        this.responseSchema = responseSchema;
        this.deprecated = deprecated;
    }
}
