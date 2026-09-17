package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRolePromptServiceTest {

    private final AgentRolePromptService service = new AgentRolePromptService();

    @Test
    void shouldResolveResearchRole() {
        assertEquals(AgentRole.RESEARCHER, service.resolve("RESEARCH", "收集国内资料"));
    }

    @Test
    void shouldResolveCoderRole() {
        assertEquals(AgentRole.CODER, service.resolve("CODE", "修改 Java 项目"));
    }

    @Test
    void shouldResolveWriterRole() {
        assertEquals(AgentRole.WRITER, service.resolve("DOCUMENT", "整理报告"));
    }

    @Test
    void shouldResolveAnalystRole() {
        assertEquals(AgentRole.ANALYST, service.resolve("ANALYSIS", "比较方案"));
    }
}
