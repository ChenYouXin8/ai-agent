package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

@Service
public class AgentTeamPlannerService {

    private final AgentRolePromptService rolePromptService;

    public AgentTeamPlannerService(AgentRolePromptService rolePromptService) {
        this.rolePromptService = rolePromptService;
    }

    public AgentAssignment assign(TaskStep step) {
        AgentRole role = rolePromptService.resolve(extractType(step.getDescription()), step.getTitle());
        String handoffPolicy = switch (role) {
            case RESEARCHER -> "交给 Analyst/Writer 时必须附带来源、关键证据与不确定性。";
            case ANALYST -> "交给 Coder/Writer 时必须附带结论、约束与证据链。";
            case CODER -> "交给后续节点时必须附带修改文件、验证结果与未解决问题。";
            case WRITER -> "交付前保留事实依据，不把历史记忆当作当前事实。";
            case GENERAL -> "交接时保留原始目标、当前结果与明确限制。";
        };
        return new AgentAssignment(role, rolePromptService.instruction(role), handoffPolicy);
    }

    private String extractType(String description) {
        if (description == null) return "GENERAL";
        String marker = "类型：";
        int index = description.indexOf(marker);
        if (index < 0) return "GENERAL";
        int start = index + marker.length();
        int end = description.indexOf('\n', start);
        return end < 0 ? description.substring(start).trim() : description.substring(start, end).trim();
    }
}
