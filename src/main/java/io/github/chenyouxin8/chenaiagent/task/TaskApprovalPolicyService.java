package io.github.chenyouxin8.chenaiagent.task;

import io.github.chenyouxin8.chenaiagent.planner.PlanStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class TaskApprovalPolicyService {

    private final boolean enabled;
    private final List<String> requiredTypes;
    private final List<String> requiredKeywords;

    public TaskApprovalPolicyService(
            @Value("${chenmanus.approval.enabled:true}") boolean enabled,
            @Value("${chenmanus.approval.required-types:DEPLOY,PURCHASE,PAYMENT}" ) String requiredTypes,
            @Value("${chenmanus.approval.required-keywords:deploy,deployment,publish,publishing,send,sending,delete,deleting,purchase,pay,payment,production,prod,shutdown,release,发布,部署,上线,发送,删除,购买,支付,生产,停机,发布配置}") String requiredKeywords
    ) {
        this.enabled = enabled;
        this.requiredTypes = split(requiredTypes);
        this.requiredKeywords = split(requiredKeywords);
    }

    public ApprovalPolicyDecision evaluate(PlanStep step) {
        if (step == null) return new ApprovalPolicyDecision(false, "无有效计划步骤");

        if (step.requiresApproval()) {
            return new ApprovalPolicyDecision(true, "Planner 明确标记为需要人工审批");
        }

        if (!enabled) {
            return new ApprovalPolicyDecision(false, "服务端风险审批策略已关闭，且 Planner 未要求审批");
        }

        String type = normalize(step.type());
        if (!type.isBlank() && requiredTypes.contains(type)) {
            return new ApprovalPolicyDecision(true, "步骤类型命中审批策略：" + type);
        }

        String searchable = String.join("\n",
                safe(step.title()), safe(step.description()), safe(step.expectedOutput()))
                .toLowerCase(Locale.ROOT);
        for (String keyword : requiredKeywords) {
            if (!keyword.isBlank() && searchable.contains(keyword)) {
                return new ApprovalPolicyDecision(true, "步骤内容命中审批策略关键词：" + keyword);
            }
        }

        return new ApprovalPolicyDecision(false, "未命中人工审批策略");
    }

    public record ApprovalPolicyDecision(boolean required, String reason) {}

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
