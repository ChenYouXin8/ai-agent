package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.common.ApiResponse;
import io.github.chenyouxin8.chenaiagent.task.TaskQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tasks/admin")
public class TaskAdminController {

    private final TaskQueueService queueService;

    public TaskAdminController(TaskQueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/dlq")
    public ApiResponse<List<String>> deadLetters() {
        return ApiResponse.ok(queueService.deadLetters(100));
    }

    @PostMapping("/dlq/replay")
    public ApiResponse<String> replayDeadLetter() {
        String payload = queueService.pollDeadLetter();
        if (payload == null || payload.isBlank()) {
            return ApiResponse.ok("DLQ 为空");
        }
        String taskId = payload.split("\\|", 2)[0];
        return ApiResponse.ok(taskId);
    }
}
