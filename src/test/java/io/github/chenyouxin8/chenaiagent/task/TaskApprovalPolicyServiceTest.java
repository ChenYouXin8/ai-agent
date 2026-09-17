package io.github.chenyouxin8.chenaiagent.task;

import io.github.chenyouxin8.chenaiagent.planner.PlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskApprovalPolicyServiceTest {
    private final TaskApprovalPolicyService service = new TaskApprovalPolicyService(
            true, "DEPLOY,PURCHASE", "deploy,publish,production,部署,发布");

    @Test
    void plannerApprovalFlagIsPreserved() {
        PlanStep step = new PlanStep("普通步骤", "只读", "GENERAL", "结果", List.of(), false, true);
        assertTrue(service.evaluate(step).required());
    }

    @Test
    void riskyTypeForcesApproval() {
        PlanStep step = new PlanStep("发布", "发布到环境", "DEPLOY", "上线", List.of(), false, false);
        assertTrue(service.evaluate(step).required());
    }

    @Test
    void riskyKeywordForcesApproval() {
        PlanStep step = new PlanStep("配置", "部署生产环境", "GENERAL", "完成", List.of(), false, false);
        assertTrue(service.evaluate(step).required());
    }

    @Test
    void normalReadOnlyStepDoesNotRequireApproval() {
        PlanStep step = new PlanStep("研究", "读取资料并分析", "RESEARCH", "摘要", List.of(), true, false);
        assertFalse(service.evaluate(step).required());
    }
}
