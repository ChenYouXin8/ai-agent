package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

@Service
public class AgentRolePromptService {

    public AgentRole resolve(String type, String title) {
        String value = ((type == null ? "" : type) + " " + (title == null ? "" : title)).toUpperCase();
        if (value.contains("RESEARCH") || value.contains("WEB") || value.contains("SEARCH")) return AgentRole.RESEARCHER;
        if (value.contains("CODE") || value.contains("PROGRAM") || value.contains("JAVA") || value.contains("VUE")) return AgentRole.CODER;
        if (value.contains("DOCUMENT") || value.contains("WRITE") || value.contains("REPORT")) return AgentRole.WRITER;
        if (value.contains("ANALYSIS") || value.contains("ANALY") || value.contains("REVIEW")) return AgentRole.ANALYST;
        return AgentRole.GENERAL;
    }

    public String instruction(AgentRole role) {
        return switch (role) {
            case RESEARCHER -> "角色：Researcher。重点是获取可靠资料、交叉核验来源、提取事实，不要凭空推断。";
            case ANALYST -> "角色：Analyst。重点是比较、推理、识别关键约束和证据，并明确结论依据。";
            case CODER -> "角色：Coder。重点是检查现有代码、实际修改文件、运行可用测试，并报告具体变更。";
            case WRITER -> "角色：Writer。重点是把已验证的信息组织成清晰、可交付的文档或报告。";
            case GENERAL -> "角色：General Agent。使用最合适的工具完成当前步骤。";
        };
    }
}
