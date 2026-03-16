package com.iems.projectservice.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectPermission {

    // ── Issues ────────────────────────────────────────────────────
    ISSUE_CREATE("Create issues"),
    ISSUE_EDIT("Edit issues"),
    ISSUE_DELETE("Delete issues"),
    ISSUE_ASSIGN("Assign issues"),
    ISSUE_TRANSITION("Change issue status"),

    // ── Sprints ───────────────────────────────────────────────────
    SPRINT_CREATE("Create sprints"),
    SPRINT_EDIT("Edit sprints"),
    SPRINT_DELETE("Delete sprints"),
    SPRINT_MANAGE("Start / complete sprints"),

    // ── Members ───────────────────────────────────────────────────
    MEMBER_INVITE("Invite members"),
    MEMBER_REMOVE("Remove members"),
    MEMBER_ROLE_ASSIGN("Assign member roles"),

    // ── Settings ──────────────────────────────────────────────────
    PROJECT_EDIT("Edit project details"),
    WORKFLOW_MANAGE("Manage workflow & statuses"),
    ROLE_MANAGE("Manage roles & permissions");

    private final String displayName;
}
