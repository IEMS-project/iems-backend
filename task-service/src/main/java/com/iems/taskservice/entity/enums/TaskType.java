package com.iems.taskservice.entity.enums;

public enum TaskType {
    EPIC("Epic"),
    TASK("Task"),
    STORY("Story"),
    BUG("Bug");

    private final String displayName;

    TaskType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}



