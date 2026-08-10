package io.github.chenyouxin8.chenaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct（Reasoning + Acting）代理基类
 *
 * <p>实现思考-行动循环：
 * 1. think() - 分析当前状态，决定是否需要执行行动
 * 2. act()   - 执行具体行动
 * 3. 重复直到 think() 返回 false 或达到最大步数
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考：分析当前状态，决定下一步
     *
     * @return true=需要执行行动，false=无需行动，对话结束
     */
    public abstract boolean think();

    /**
     * 执行具体行动
     *
     * @return 行动结果
     */
    public abstract String act();

    /**
     * 执行一步思考-行动循环
     *
     * @return 当前步骤的执行结果
     */
    @Override
    public String step() {
        try {
            boolean shouldContinue = think();
            if (!shouldContinue) {
                // think() 已将模型最终回复存入 lastThinkResult
                return getLastThinkResult() != null
                        ? getLastThinkResult()
                        : "思考完成 - 无需行动";
            }
            return act();
        } catch (Exception e) {
            log.error("步骤执行失败", e);
            return "步骤执行失败: " + e.getMessage();
        }
    }
}