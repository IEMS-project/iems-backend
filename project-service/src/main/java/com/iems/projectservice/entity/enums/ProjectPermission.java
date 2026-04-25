package com.iems.projectservice.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectPermission {

    // ── Project ───────────────────────────────────────────────────
    PROJECT_READ("Read project details"),
    PROJECT_CREATE("Create projects"),
    PROJECT_UPDATE("Update project details"),
    PROJECT_DELETE("Delete project"),

    // ── Issues ────────────────────────────────────────────────────
    ISSUE_READ("Read issues"),
    ISSUE_CREATE("Create issues"),
    ISSUE_UPDATE("Update issues"),
    ISSUE_DELETE("Delete issues"),

    // ── Workflow ──────────────────────────────────────────────────
    WORKFLOW_READ("Read workflows"),
    WORKFLOW_CREATE("Create workflows"),
    WORKFLOW_UPDATE("Update workflows"),
    WORKFLOW_DELETE("Delete workflows"),

    // ── Roles ─────────────────────────────────────────────────────
    ROLE_READ("Read roles & permissions"),
    ROLE_CREATE("Create roles & permission assignments"),
    ROLE_UPDATE("Update roles & permission assignments"),
    ROLE_DELETE("Delete roles & permission assignments"),

    // ── Sprints ───────────────────────────────────────────────────
    SPRINT_READ("Read sprints"),
    SPRINT_CREATE("Create sprints"),
    SPRINT_UPDATE("Update sprints"),
    SPRINT_DELETE("Delete sprints"),

    // ── Members ───────────────────────────────────────────────────
    MEMBER_INVITE("Invite members"),
    MEMBER_REMOVE("Remove members"),
    MEMBER_ROLE_ASSIGN("Assign member roles"),

    // ── Documents ──────────────────────────────────────────────────
    DOCUMENT_VIEW("View project documents"),
    DOCUMENT_MODIFY("Modify project documents");



    private final String displayName;
}
