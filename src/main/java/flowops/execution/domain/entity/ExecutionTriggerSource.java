package flowops.execution.domain.entity;

public enum ExecutionTriggerSource {
    MANUAL,
    INTERNAL,
    PR_MERGE,
    DEPLOY,
    SCHEDULE
}
