package com.fixflow.workspace.domain;

public enum MembershipStatus {

    INVITED,
    PENDING,
    ACTIVE,
    SUSPENDED,
    REMOVED,
    REJECTED;

    public boolean canAccessWorkspace() {
        return this == ACTIVE;
    }
}
