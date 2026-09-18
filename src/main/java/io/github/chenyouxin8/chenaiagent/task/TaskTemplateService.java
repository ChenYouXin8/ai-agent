package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskTemplateService {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]{1,64})\\s*}}" );

    private final TaskAutomationRepository repository;

    public TaskTemplateService(TaskAutomationRepository repository) {
        this.repository = repository;
    }

    public TaskTemplate create(String name, String promptTemplate, String tenantId, String ownerId,
                               TaskPriority priority, boolean approvalRequired) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("模板名称不能为空");
        if (promptTemplate == null || promptTemplate.isBlank()) throw new IllegalArgumentException("模板内容不能为空");
        if (promptTemplate.length() > 20000) throw new IllegalArgumentException("模板内容过长");
        TaskTemplate template = new TaskTemplate(
                "tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                name.trim(), promptTemplate.trim(), normalize(tenantId, "default"), normalize(ownerId, "anonymous"),
                priority, approvalRequired);
        repository.saveTemplate(template);
        return template;
    }

    public TaskTemplate get(String tenantId, String templateId) {
        TaskTemplate template = repository.findTemplate(tenantId, templateId);
        if (template == null) throw new IllegalArgumentException("任务模板不存在");
        return template;
    }

    public String render(TaskTemplate template, Map<String, ?> variables) {
        String source = template.getPromptTemplate();
        Map<String, ?> safe = variables == null ? Map.of() : variables;
        Matcher matcher = VARIABLE.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = safe.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString().trim();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String v = value.trim();
        return v.substring(0, Math.min(128, v.length()));
    }
}
