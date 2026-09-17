package io.github.chenyouxin8.chenaiagent.task;

public record ReviewDecision(
        boolean passed,
        String feedback,
        String missingItems
) {
    public static ReviewDecision fallback(String reason) {
        return new ReviewDecision(true,
                reason == null || reason.isBlank() ? "基础验收完成" : "基础验收完成：" + reason,
                "");
    }
}
