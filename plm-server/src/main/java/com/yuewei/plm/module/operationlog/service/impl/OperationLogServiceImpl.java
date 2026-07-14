package com.yuewei.plm.module.operationlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.common.util.RequestIdUtil;
import com.yuewei.plm.common.vo.PageVO;
import com.yuewei.plm.module.operationlog.dto.OperationLogQueryDTO;
import com.yuewei.plm.module.operationlog.entity.OperationLog;
import com.yuewei.plm.module.operationlog.repository.OperationLogRepository;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.operationlog.vo.OperationLogVO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogRepository operationLogRepository;

    @Override
    @Transactional
    public Long logSuccess(OperationLogCreateCommand command) {
        // 操作人和 requestId 在服务层统一取值，避免各业务接口各自拼装导致审计字段不一致。
        CurrentUser currentUser = CurrentUserContext.get().orElse(null);
        String operatorName = currentUser == null ? null : currentUser.displayName();
        OperationLog log = new OperationLog();
        log.setRequestId(RequestIdUtil.getRequestId(command.getRequest()));
        log.setOperatorUserId(currentUser == null ? null : currentUser.userId());
        log.setOperatorUserName(operatorName);
        log.setAction(command.getAction());
        log.setBusinessType(command.getBusinessType());
        log.setBusinessId(command.getBusinessId());
        log.setBusinessCode(command.getBusinessCode());
        log.setBusinessName(command.getBusinessName());
        log.setResult("success");
        log.setRequestMethod(command.getRequest() == null ? null : command.getRequest().getMethod());
        log.setRequestUri(command.getRequest() == null ? null : command.getRequest().getRequestURI());
        log.setClientIp(command.getRequest() == null ? null : command.getRequest().getRemoteAddr());
        log.setUserAgent(command.getRequest() == null ? null : command.getRequest().getHeader("User-Agent"));
        log.setDetailJson(command.getDetailJson());
        log.setCreatedAt(LocalDateTime.now());
        log.setCreatedBy(StringUtils.hasText(operatorName) ? operatorName : "system");
        log.setUpdatedAt(LocalDateTime.now());
        log.setUpdatedBy(StringUtils.hasText(operatorName) ? operatorName : "system");
        log.setDeletedFlag(0);
        operationLogRepository.insert(log);
        return log.getLogId();
    }

    @Override
    public PageVO<OperationLogVO> page(OperationLogQueryDTO queryDTO) {
        LambdaQueryWrapper<OperationLog> queryWrapper = new LambdaQueryWrapper<OperationLog>()
            .eq(StringUtils.hasText(queryDTO.getRequestId()), OperationLog::getRequestId, queryDTO.getRequestId())
            .eq(queryDTO.getOperatorUserId() != null, OperationLog::getOperatorUserId, queryDTO.getOperatorUserId())
            .eq(StringUtils.hasText(queryDTO.getAction()), OperationLog::getAction, queryDTO.getAction())
            .eq(StringUtils.hasText(queryDTO.getBusinessType()), OperationLog::getBusinessType, queryDTO.getBusinessType())
            .eq(StringUtils.hasText(queryDTO.getBusinessId()), OperationLog::getBusinessId, queryDTO.getBusinessId())
            .orderByDesc(OperationLog::getCreatedAt);
        IPage<OperationLog> page = operationLogRepository.selectPage(
            new Page<>(queryDTO.getPage(), queryDTO.getSize()),
            queryWrapper
        );
        return PageVO.<OperationLogVO>builder()
            .content(page.getRecords().stream().map(OperationLogVO::from).toList())
            .page(page.getCurrent())
            .size(page.getSize())
            .totalElements(page.getTotal())
            .totalPages(page.getPages())
            .build();
    }
}
