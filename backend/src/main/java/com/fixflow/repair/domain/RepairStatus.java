package com.fixflow.repair.domain;

public enum RepairStatus {
    RECEIVED,
    DIAGNOSING,
    WAITING_FOR_PART,
    IN_REPAIR,
    READY,
    DELIVERED,
    CANCELLED
}
