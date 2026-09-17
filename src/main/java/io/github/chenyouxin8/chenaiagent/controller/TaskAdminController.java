package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.common.ApiResponse;
import io.github.chenyouxin8.chenaiagent.task.ChenTask;
import io.github.chenyouxin8.chenaiagent.task.TaskManager;
import io.github.chenyouxin8.chenaiagent.task.TaskQueueService;
import io.github.chenyouxin8.chenaiagent.task.TaskRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tasks/admin")
public class TaskAdminController {

    private final TaskQueueService queueService;
    private final TaskManager taskManager;
    private final TaskRuntimeService runtime;

    public TaskAdminController(
            TaskQueueService queueService,
            TaskManager taskManager,
            TaskRuntimeService runtime
    ) {
        this.queueService = queueService;
        this.taskManager = taskManager;
        this.runtime = runtime;
    }

    @GetMapping("/dlq")
    public ApiResponse<List<String>> deadLetters() {
        return ApiResponse.ok(queueService.deadLetters(100));
    }

    @PostMapping("/dlq/replay")
    public ApiResponse<String> replayDeadLetter(@RequestParam(required = false) String taskId) {
        String selectedTaskId = taskId;
        if (selectedTaskId == null || selectedTaskId.isBlank()) {
            String payload = queueService.pollDeadLetter();
            if (payload == null || payload.isBlank()) return ApiResponse.ok("DLQ 为空");
            selectedTaskId = payload.split("\\|", 2)[0];
        }

        ChenTask task = taskManager.get(selectedTaskId);
        if (task.getStatus() == io.github.chenyouxin8.chenaiagent.task.TaskStatus.COMPLETED
                || task.getStatus() == io.github.chenyouxin8.chenaiagent.task.TaskStatus.CANCELLED) {
            return ApiResponse.badRequest("任务当前状态不可重放：" + task.getStatus());
        }
        runtime.start(selectedTaskId);
        return ApiResponse.ok(selectedTaskId);
    }
}
