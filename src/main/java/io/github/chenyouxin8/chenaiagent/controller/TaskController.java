package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.common.ApiResponse;
import io.github.chenyouxin8.chenaiagent.task.ChenTask;
import io.github.chenyouxin8.chenaiagent.task.RequestIdentityService;
import io.github.chenyouxin8.chenaiagent.task.TaskEvent;
import io.github.chenyouxin8.chenaiagent.task.TaskEventType;
import io.github.chenyouxin8.chenaiagent.task.TaskManager;
import io.github.chenyouxin8.chenaiagent.task.TaskPriority;
import io.github.chenyouxin8.chenaiagent.task.TaskQuotaService;
import io.github.chenyouxin8.chenaiagent.task.TaskRuntimeService;
import io.github.chenyouxin8.chenaiagent.task.TaskScopeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskManager taskManager;
    private final TaskRuntimeService runtime;
    private final TaskScopeService scopeService;
    private final TaskQuotaService quotaService;
    private final RequestIdentityService identityService;

    public TaskController(
            TaskManager taskManager,
            TaskRuntimeService runtime,
            TaskScopeService scopeService,
            TaskQuotaService quotaService,
            RequestIdentityService identityService
    ) {
        this.taskManager = taskManager;
        this.runtime = runtime;
        this.scopeService = scopeService;
        this.quotaService = quotaService;
        this.identityService = identityService;
    }

    @PostMapping
    public ApiResponse<ChenTask> create(
            @RequestBody CreateTaskRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return ApiResponse.badRequest("任务内容不能为空");
        }
        String tenantId = identityService.tenantId(httpRequest, request.tenantId());
        String userId = identityService.userId(httpRequest, request.userId());
        String sessionId = scopeService.normalize(request.sessionId(), "default");
        quotaService.assertCanCreate(tenantId);
        ChenTask task = taskManager.create(
                request.prompt().trim(), tenantId, userId, sessionId, TaskPriority.from(request.priority()));
        runtime.start(task.getTaskId());
        return ApiResponse.ok(task);
    }

    @GetMapping
    public ApiResponse<List<ChenTask>> list(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest httpRequest
    ) {
        String effectiveTenant = identityService.tenantId(httpRequest, tenantId);
        String effectiveUser = identityService.userId(httpRequest, userId);
        return ApiResponse.ok(taskManager.list(effectiveTenant, effectiveUser, sessionId));
    }

    @GetMapping("/quota")
    public ApiResponse<QuotaView> quota(
            @RequestParam(required = false) String tenantId,
            HttpServletRequest httpRequest
    ) {
        String normalizedTenant = identityService.tenantId(httpRequest, tenantId);
        return ApiResponse.ok(new QuotaView(
                normalizedTenant,
                quotaService.activeTasks(normalizedTenant),
                quotaService.maxActiveTasksPerTenant()
        ));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ChenTask> get(
            @PathVariable String taskId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            HttpServletRequest httpRequest
    ) {
        ChenTask task = taskManager.get(taskId);
        scopeService.assertAccess(
                task,
                identityService.tenantId(httpRequest, tenantId),
                identityService.userId(httpRequest, userId));
        return ApiResponse.ok(task);
    }

    @PostMapping("/{taskId}/pause")
    public ApiResponse<ChenTask> pause(
            @PathVariable String taskId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            HttpServletRequest httpRequest
    ) {
        ChenTask task = taskManager.get(taskId);
        scopeService.assertAccess(
                task,
                identityService.tenantId(httpRequest, tenantId),
                identityService.userId(httpRequest, userId));
        runtime.pause(taskId);
        return ApiResponse.ok(taskManager.get(taskId));
    }

    @PostMapping("/{taskId}/resume")
    public ApiResponse<ChenTask> resume(
            @PathVariable String taskId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            HttpServletRequest httpRequest
    ) {
        ChenTask task = taskManager.get(taskId);
        scopeService.assertAccess(
                task,
                identityService.tenantId(httpRequest, tenantId),
                identityService.userId(httpRequest, userId));
        runtime.resume(taskId);
        return ApiResponse.ok(taskManager.get(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<ChenTask> cancel(
            @PathVariable String taskId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            HttpServletRequest httpRequest
    ) {
        ChenTask task = taskManager.get(taskId);
        scopeService.assertAccess(
                task,
                identityService.tenantId(httpRequest, tenantId),
                identityService.userId(httpRequest, userId));
        runtime.cancel(taskId);
        return ApiResponse.ok(taskManager.get(taskId));
    }

    @GetMapping("/{taskId}/events")
    public SseEmitter events(
            @PathVariable String taskId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            HttpServletRequest httpRequest
    ) {
        ChenTask task = taskManager.get(taskId);
        scopeService.assertAccess(
                task,
                identityService.tenantId(httpRequest, tenantId),
                identityService.userId(httpRequest, userId));
        SseEmitter emitter = new SseEmitter(0L);
        Consumer<TaskEvent> listener = event -> {
            try {
                emitter.send(SseEmitter.event().name(event.getType().name().toLowerCase()).data(event));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        };
        taskManager.subscribe(taskId, listener);
        emitter.onCompletion(() -> taskManager.unsubscribe(taskId, listener));
        emitter.onTimeout(() -> taskManager.unsubscribe(taskId, listener));
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(new TaskEvent(taskId, TaskEventType.MESSAGE, null, "已连接 ChenManus 2.7 实时事件流")));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public record CreateTaskRequest(
            String prompt,
            String tenantId,
            String userId,
            String sessionId,
            String priority
    ) {}

    public record QuotaView(String tenantId, int activeTasks, int maxActiveTasksPerTenant) {}
}
