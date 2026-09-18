package io.github.chenyouxin8.chenaiagent.task;

public enum TaskPriority {
    LOW(10),
    NORMAL(20),
    HIGH(30),
    CRITICAL(40);

    private final int weight;

    TaskPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public static TaskPriority from(String value) {
        if (value == null || value.isBlank()) return NORMAL;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }
}
