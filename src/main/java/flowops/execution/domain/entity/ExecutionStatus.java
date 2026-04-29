package flowops.execution.domain.entity;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    PARTIAL_FAILED,
    FAILED,
    CANCELED
}
