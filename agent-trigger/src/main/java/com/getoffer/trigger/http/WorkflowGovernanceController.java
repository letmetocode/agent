package com.getoffer.trigger.http;

import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.command.WorkflowGovernanceApplicationService;
import com.getoffer.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Workflow 治理 API（协议层）。
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowGovernanceController {

    private final WorkflowGovernanceApplicationService governanceApplicationService;

    public WorkflowGovernanceController(WorkflowGovernanceApplicationService governanceApplicationService) {
        this.governanceApplicationService = governanceApplicationService;
    }

    @GetMapping("/drafts")
    public Response<List<Map<String, Object>>> listDrafts(
            @RequestParam(value = "status", required = false) String statusText) {
        return success(governanceApplicationService.listDrafts(statusText));
    }

    @GetMapping("/drafts/{id}")
    public Response<Map<String, Object>> getDraftDetail(@PathVariable("id") Long id) {
        try {
            return success(governanceApplicationService.getDraftDetail(id));
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        }
    }

    @PostMapping("/sop-spec/drafts/{id}/compile")
    public Response<Map<String, Object>> compileDraftSopSpec(@PathVariable("id") Long id,
                                                             @RequestBody(required = false) Map<String, Object> request) {
        try {
            return success(governanceApplicationService.compileDraftSopSpec(id, request));
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        }
    }

    @PostMapping("/sop-spec/drafts/{id}/validate")
    public Response<Map<String, Object>> validateDraftSopSpec(@PathVariable("id") Long id,
                                                              @RequestBody(required = false) Map<String, Object> request) {
        try {
            return success(governanceApplicationService.validateDraftSopSpec(id, request));
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        }
    }

    @PutMapping("/drafts/{id}")
    public Response<Map<String, Object>> updateDraft(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) Map<String, Object> request) {
        try {
            return success(governanceApplicationService.updateDraft(id, request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return failure(ex.getMessage());
        }
    }

    @PostMapping("/drafts/{id}/publish")
    public Response<Map<String, Object>> publishDraft(@PathVariable("id") Long id,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        try {
            return success(governanceApplicationService.publishDraft(id, request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return failure(ex.getMessage());
        }
    }

    @GetMapping("/definitions")
    public Response<List<Map<String, Object>>> listDefinitions(
            @RequestParam(value = "status", required = false) String statusText) {
        return success(governanceApplicationService.listDefinitions(statusText));
    }

    @GetMapping("/definitions/{id}")
    public Response<Map<String, Object>> getDefinition(@PathVariable("id") Long id) {
        try {
            return success(governanceApplicationService.getDefinition(id));
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        }
    }

    private Response<Map<String, Object>> failure(String info) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(info)
                .build();
    }

    private Response<Map<String, Object>> success(Map<String, Object> data) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private Response<List<Map<String, Object>>> success(List<Map<String, Object>> data) {
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
