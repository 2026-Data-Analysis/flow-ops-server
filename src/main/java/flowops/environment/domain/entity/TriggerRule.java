package flowops.environment.domain.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Getter
@Entity
@Table(name = "trigger_rules")
@Comment("환경별 자동 실행 트리거 규칙")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TriggerRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("트리거 규칙 식별자")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    @Comment("트리거가 속한 환경")
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    @Comment("트리거 유형")
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    @Comment("트리거 적용 범위")
    private TriggerScopeType scopeType;

    @Column(name = "scope_value", length = 2000)
    @Comment("선택 API ID 목록 또는 태그 목록")
    private String scopeValue;

    @Column(name = "trigger_config", columnDefinition = "TEXT")
    @Comment("cron 등 트리거 상세 설정 JSON")
    private String triggerConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    @Comment("트리거 실행 모드")
    private ExecutionMode executionMode;

    @Column(nullable = false)
    @Comment("트리거 활성 여부")
    private boolean enabled;

    @Column(name = "last_triggered_at")
    @Comment("마지막 자동 실행 시각")
    private LocalDateTime lastTriggeredAt;

    @Builder
    private TriggerRule(
            Environment environment,
            TriggerType triggerType,
            TriggerScopeType scopeType,
            String scopeValue,
            String triggerConfig,
            ExecutionMode executionMode,
            boolean enabled,
            LocalDateTime lastTriggeredAt
    ) {
        this.environment = environment;
        this.triggerType = triggerType;
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
        this.triggerConfig = triggerConfig;
        this.executionMode = executionMode;
        this.enabled = enabled;
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public void update(
            TriggerType triggerType,
            TriggerScopeType scopeType,
            String scopeValue,
            String triggerConfig,
            ExecutionMode executionMode,
            boolean enabled
    ) {
        this.triggerType = triggerType;
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
        this.triggerConfig = triggerConfig;
        this.executionMode = executionMode;
        this.enabled = enabled;
    }

    public void markTriggered(LocalDateTime triggeredAt) {
        this.lastTriggeredAt = triggeredAt;
    }
}
