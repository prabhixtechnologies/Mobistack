package com.fixflow.group.domain;

/**
 * Rights inside a fitment group. The creator is the owner and is stored on the group,
 * not as a member row, so an admin cannot remove them.
 */
public enum GroupRole {
    MEMBER,
    ADMIN,
    OWNER
}
