package com.iems.iamservice.entity.enums;

import java.util.Arrays;

public enum PremiumPlan {
    WEEK("week", 49_000L, 7, "IEMS Premium Week"),
    MONTH("month", 149_000L, 30, "IEMS Premium Month"),
    YEAR("year", 1_499_000L, 365, "IEMS Premium Year");

    private final String id;
    private final long amount;
    private final int durationDays;
    private final String description;

    PremiumPlan(String id, long amount, int durationDays, String description) {
        this.id = id;
        this.amount = amount;
        this.durationDays = durationDays;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public long getAmount() {
        return amount;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public String getDescription() {
        return description;
    }

    public static PremiumPlan fromId(String id) {
        return Arrays.stream(values())
                .filter(plan -> plan.id.equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported premium plan: " + id));
    }
}
