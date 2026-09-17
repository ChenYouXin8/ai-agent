package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.common.ApiResponse;
import io.github.chenyouxin8.chenaiagent.task.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Arrays;
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
    private final TaskAuditService auditService;
    private final TaskApprovalService approvalService;

    public TaskController(TaskManager taskManager, TaskRuntimeService runtime, TaskScopeService scopeService,
                          TaskQuotaService quotaService, RequestIdentityService identityService,
                          TaskAuditService auditService, TaskApprovalService approvalService) {
        this.taskManager = taskManager;
        this.runtime = runtime;
        this.scopeService = scopeService;
        this.quotaService = quotaService;
        this.identityService = identityService;
        this.auditService = auditService;
        this.approvalService = approvalService;
    }

    @PostMapping
    public ApiResponse<ChenTask> create(@RequestBody CreateTaskRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) return ApiResponse.badRequest("任务内容不能为空");
        String tenantId = identityService.tenantId(httpRequest, request.tenantId());
        String userId = identityService.userId(httpRequest, request.userId());
        String sessionId = scopeService.normalize(request.sessionId(), "default");
        quotaService.assertCanCreate(tenantId);
        ChenTask task = taskManager.create(request.prompt().trim(), tenantId, userId, sessionId, TaskPriority.from(request.priority()));
        runtime.start(task.getTaskId());
        return ApiResponse.ok(task);
    }

    @GetMapping
    public ApiResponse<List<ChenTask>> list(@RequestParam(required = false) String tenantId,
                                             @RequestParam(required = false) String userId,
                                             @RequestParam(required = false) String sessionId,
                                             HttpServletRequest httpRequest) {
        String effectiveTenant = identityService.tenantId(httpRequest, tenantId);
        String effectiveUser = identityService.isAdmin(httpRequest) ? null : identityService.userId(httpRequest, userId);
        return ApiResponse.ok(taskManager.list(effectiveTenant, effectiveUser, sessionId));
    }

    @GetMapping("/quota")
    public ApiResponse<QuotaView> quota(@RequestParam(required = false) String tenantId, HttpServletRequest httpRequest) {
        String normalizedTenant = identityService.tenantId(httpRequest, tenantId);
        return ApiResponse.ok(new QuotaView(normalizedTenant, quotaService.activeTasks(normalizedTenant), quotaService.maxActiveTasksPerTenant()));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ChenTask> get(@PathVariable String taskId, @RequestParam(required = false) String tenantId,
                                     @RequestParam(required = false) String userId, HttpServletRequest request) {
        return ApiResponse.ok(authorize(taskId, tenantId, userId, request));
    }

    @PostMapping("/{taskId}/pause")
    public ApiResponse<ChenTask> pause(@PathVariable String taskId, @RequestParam(required = false) String tenantId,
                                       @RequestParam(required = false) String userId, HttpServletRequest request) {
        authorize(taskId, tenantId, userId, request); runtime.pause(taskId); return ApiResponse.ok(taskManager.get(taskId));
    }

    @PostMapping("/{taskId}/resume")
    public ApiResponse<ChenTask> resume(@PathVariable String taskId, @RequestParam(required = false) String tenantId,
                                        @RequestParam(required = false) String userId, HttpServletRequest request) {
        authorize(taskId, tenantId, userId, request); runtime.resume(taskId); return ApiResponse.ok(taskManager.get(taskId));
    }

    @PostMapping("/{taskId}/approve")
    public ApiResponse<ChenTask> approve(@PathVariable String taskId, @RequestParam(required = false) String tenantId,
                                         @RequestParam(required = false) String userId, @RequestBody(required = false) ApprovalRequest body,
                                         HttpServletRequest request) {
        authorize(taskId, tenantId, userId, request); requireApprovalAdmin(request);
        approvalService.approve(taskId, body == null ? "" : body.note(), identityService.userId(request, userId));
        return ApiResponse.ok(taskManager.get(taskId));
    }

    @PostMapping("/{taskId}/reject")
    public ApiResponse<ChenTask> reject(@PathVariable String taskId, @RequestParam(required = false) String tenantId,
                                        @RequestParam(required = false) String userId, @RequestBody(required = false) ApprovalRequest body,
                                        HttpServletRequest request) {
        authorize(taskId, tenantId, userId, request); requireApprovalAdmin(request);
        approvalService.reject(taskId, body == null ? "" : body.note(), identityService.userId(request, userId));
        return ApiResponse.ok(taskManager.get(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<ChenTask> cancel(@PathVariable String taskId, @RequestParam(required = false) String tenantId,
                                        @RequestParam(required = false) String userId, HttpServletRequest request) {
        authorize(taskId, tenantId, userId, request); runtime.cancel(taskId); return ApiResponse.ok(taskManager.get(taskId));
    }

    @GetMapping("/{taskId}/events/history")
    public ApiResponse<List<TaskEvent>> eventHistory(@PathVariable String taskId,
                                                      @RequestParam(required = false, defaultValue = "200") int limit,
                                                      @RequestParam(required = false, defaultValue = "0") long from,
                                                      @RequestParam(required = false, defaultValue = "0") long to,
                                                      @RequestParam(required = false) String types,
                                                      @RequestParam(required = false) String stepId,
                                                      @RequestParam(required = false) String tenantId,
                                                      @RequestParam(required = false) String userId,
                                                      HttpServletRequest request) {
        authorize(taskId, tenantId, userId, request);
        List<TaskEventType> eventTypes = parseTypes(types);
        return ApiResponse.ok(auditService.history(taskId, new TaskAuditQuery(limit, from, to, eventTypes, stepId)));
    }

    @GetMapping("/{taskId}/events")
    public SseEmitter events(@PathVariable String taskId, @RequestParam(required = false) String tenantId,
                             @RequestParam(required = false) String userId, HttpServletRequest request) {
        authorize(taskId, tenantId, userId, request);
        SseEmitter emitter = new SseEmitter(0L);
        Consumer<TaskEvent> listener = event -> {
            try { emitter.send(SseEmitter.event().name(event.getType().name().toLowerCase()).data(event)); }
            catch (IOException e) { emitter.completeWithError(e); }
        };
        taskManager.subscribe(taskId, listener);
        emitter.onCompletion(() -> taskManager.unsubscribe(taskId, listener));
        emitter.onTimeout(() -> taskManager.unsubscribe(taskId, listener));
        try {
            for (TaskEvent event : taskManager.history(taskId, 200)) emitter.send(SseEmitter.event().name(event.getType().name().toLowerCase()).data(event));
            emitter.send(SseEmitter.event().name("connected").data(new TaskEvent(taskId, TaskEventType.MESSAGE, null, "已连接 ChenManus 2.9 审计事件流")));
        } catch (IOException e) { emitter.completeWithError(e); }
        return emitter;
    }

    private List<TaskEventType> parseTypes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(value -> {
            try { return TaskEventType.valueOf(value.toUpperCase()); }
            catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知事件类型：" + value); }
        }).distinct().toList();
    }

    private void requireApprovalAdmin(HttpServletRequest request) {
        if (!identityService.canApprove(request)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前身份没有人工审批权限");
    }

    private ChenTask authorize(String taskId, String tenantId, String userId, HttpServletRequest request) {
        ChenTask task = taskManager.get(taskId);
        scopeService.assertAccess(task, identityService.tenantId(request, tenantId), identityService.userId(request, userId));
        return task;
    }

    public record CreateTaskRequest(String prompt, String tenantId, String userId, String sessionId, String priority) {}
    public record ApprovalRequest(String note) {}
    public record QuotaView(String tenantId, int activeTasks, int maxActiveTasksPerTenant) {}
}
