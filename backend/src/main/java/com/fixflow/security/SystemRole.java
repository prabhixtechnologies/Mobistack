package com.fixflow.security;

public enum SystemRole {

    OWNER,
    ADMIN,
    MANAGER,
    TECHNICIAN,
    STAFF,
    VIEWER;

    public static boolean isSystemRole(String code) {
        for (SystemRole role : values()) {
            if (role.name().equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
