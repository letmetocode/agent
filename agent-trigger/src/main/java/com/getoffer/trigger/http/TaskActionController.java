package com.getoffer.trigger.http;

import com.getoffer.api.dto.TaskDetailDTO;
import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.command.TaskActionCommandService;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 任务控制与产物导出 API（HTTP 适配层）。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskActionController {

    private final TaskActionCommandService taskActionCommandService;

    public TaskActionController(TaskActionCommandService taskActionCommandService) {
        this.taskActionCommandService = taskActionCommandService;
    }

    @PostMapping("/{id}/pause")
    public Response<TaskDetailDTO> pause(@PathVariable("id") Long taskId) {
        try {
            return success(taskActionCommandService.pause(taskId));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @PostMapping("/{id}/resume")
    public Response<TaskDetailDTO> resume(@PathVariable("id") Long taskId) {
        try {
            return success(taskActionCommandService.resume(taskId));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    public Response<TaskDetailDTO> cancel(@PathVariable("id") Long taskId) {
        try {
            return success(taskActionCommandService.cancel(taskId));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @PostMapping("/{id}/retry-from-failed")
    public Response<TaskDetailDTO> retryFromFailed(@PathVariable("id") Long taskId) {
        try {
            return success(taskActionCommandService.retryFromFailed(taskId));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @GetMapping("/{id}/export")
    public Response<Map<String, Object>> exportTask(@PathVariable("id") Long taskId,
                                                    @RequestParam(value = "format", required = false) String format) {
        try {
            return success(taskActionCommandService.exportTask(taskId, format));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @PostMapping("/{id}/share-links")
    public Response<Map<String, Object>> createShareLinks(@PathVariable("id") Long taskId,
                                                           @RequestParam(value = "expiresHours", required = false) Integer expiresHours) {
        try {
            return success(taskActionCommandService.createShareLink(taskId, expiresHours));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @GetMapping("/{id}/share-links")
    public Response<List<Map<String, Object>>> listShareLinks(@PathVariable("id") Long taskId) {
        try {
            return success(taskActionCommandService.listShareLinks(taskId));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @PostMapping("/{id}/share-links/{shareId}/revoke")
    public Response<Map<String, Object>> revokeShareLink(@PathVariable("id") Long taskId,
                                                          @PathVariable("shareId") Long shareId,
                                                          @RequestParam(value = "reason", required = false) String reason) {
        try {
            return success(taskActionCommandService.revokeShareLink(taskId, shareId, reason));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    @PostMapping("/{id}/share-links/revoke-all")
    public Response<Map<String, Object>> revokeAllShareLinks(@PathVariable("id") Long taskId,
                                                              @RequestParam(value = "reason", required = false) String reason) {
        try {
            return success(taskActionCommandService.revokeAllShareLinks(taskId, reason));
        } catch (AppException ex) {
            return illegal(ex.getMessage());
        }
    }

    private <T> Response<T> illegal(String message) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
