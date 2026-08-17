package com.yuewei.plm.module.workflow.controller;

import com.yuewei.plm.common.constant.ApiConstants;
import com.yuewei.plm.common.util.RequestIdUtil;
import com.yuewei.plm.common.vo.ResponseVO;
import com.yuewei.plm.module.workflow.dto.WorkflowTemplateSaveDTO;
import com.yuewei.plm.module.workflow.service.WorkflowTemplateService;
import com.yuewei.plm.module.workflow.vo.WorkflowTemplateVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.API_V1_PREFIX + "/approval-center")
public class WorkflowTemplateController {

    private final WorkflowTemplateService workflowTemplateService;

    @GetMapping("/tasks")
    public ResponseVO<List<Map<String, Object>>> tasks(HttpServletRequest request) {
        return ResponseVO.success(List.of(), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @GetMapping("/workflow-templates")
    public ResponseVO<List<WorkflowTemplateVO>> list(@RequestParam(required = false) String flowType,
                                                     @RequestParam(required = false) String status,
                                                     HttpServletRequest request) {
        return ResponseVO.success(workflowTemplateService.list(flowType, status), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @GetMapping("/workflow-templates/{workflowTemplateId}")
    public ResponseVO<WorkflowTemplateVO> detail(@PathVariable Long workflowTemplateId, HttpServletRequest request) {
        return ResponseVO.success(workflowTemplateService.detail(workflowTemplateId), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PostMapping("/workflow-templates")
    public ResponseVO<WorkflowTemplateVO> create(@Valid @RequestBody WorkflowTemplateSaveDTO dto, HttpServletRequest request) {
        return ResponseVO.created(workflowTemplateService.create(dto), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PutMapping("/workflow-templates/{workflowTemplateId}")
    public ResponseVO<WorkflowTemplateVO> update(@PathVariable Long workflowTemplateId,
                                                 @Valid @RequestBody WorkflowTemplateSaveDTO dto,
                                                 HttpServletRequest request) {
        return ResponseVO.success(workflowTemplateService.update(workflowTemplateId, dto), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PostMapping("/workflow-templates/{workflowTemplateId}/activate")
    public ResponseVO<WorkflowTemplateVO> activate(@PathVariable Long workflowTemplateId, HttpServletRequest request) {
        return ResponseVO.success(workflowTemplateService.activate(workflowTemplateId), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PostMapping("/workflow-templates/{workflowTemplateId}/copy")
    public ResponseVO<WorkflowTemplateVO> copy(@PathVariable Long workflowTemplateId,
                                               @RequestParam(required = false) String targetFlowType,
                                               HttpServletRequest request) {
        return ResponseVO.created(workflowTemplateService.copy(workflowTemplateId, targetFlowType), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @GetMapping("/template-options")
    public ResponseVO<Map<String, Object>> options(HttpServletRequest request) {
        Map<String, Object> options = Map.of(
            "flowTypes", List.of(
                Map.of("label", "新产品线", "value", "product_line"),
                Map.of("label", "新型号线", "value", "model_variant")
            ),
            "statuses", List.of(
                Map.of("label", "草稿", "value", "draft"),
                Map.of("label", "启用", "value", "active"),
                Map.of("label", "停用", "value", "inactive")
            ),
            "fileCategories", List.of(
                Map.of("label", "图纸资料", "value", "drawing"),
                Map.of("label", "测试资料", "value", "testing"),
                Map.of("label", "SOP/SIP", "value", "sop"),
                Map.of("label", "客户确认资料", "value", "customer_confirm"),
                Map.of("label", "其他资料", "value", "other")
            )
        );
        return ResponseVO.success(options, RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }
}
