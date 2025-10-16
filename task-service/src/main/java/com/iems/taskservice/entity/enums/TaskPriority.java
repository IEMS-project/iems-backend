package com.iems.taskservice.entity.enums;

public enum TaskPriority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String displayName;

    TaskPriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TaskPriority fromDisplayName(String displayName) {
        for (TaskPriority priority : values()) {
            if (priority.displayName.equals(displayName)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown task priority: " + displayName);
    }
}
