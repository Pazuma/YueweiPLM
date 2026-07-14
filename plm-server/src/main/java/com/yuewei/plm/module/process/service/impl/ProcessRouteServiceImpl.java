package com.yuewei.plm.module.process.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.process.dto.ProcessOperationDTO;
import com.yuewei.plm.module.process.dto.ProcessRouteSaveDTO;
import com.yuewei.plm.module.process.entity.ProcessEntity;
import com.yuewei.plm.module.process.repository.ProcessRepository;
import com.yuewei.plm.module.process.service.ProcessRouteService;
import com.yuewei.plm.module.process.vo.ProcessOperationVO;
import com.yuewei.plm.module.process.vo.ProcessRouteVO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProcessRouteServiceImpl implements ProcessRouteService {

    private static final String TYPE_ROUTING = "routing";
    private static final String TYPE_OPERATION = "operation";
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_LOCKED = "locked";

    private final ProductRepository productRepository;
    private final ProcessRepository processRepository;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Override
    public List<ProcessRouteVO> listByProject(Long projectId) {
        getProductOrThrow(projectId);
        return processRepository.selectList(new LambdaQueryWrapper<ProcessEntity>()
                .eq(ProcessEntity::getProductId, projectId)
                .eq(ProcessEntity::getProcessType, TYPE_ROUTING)
                .eq(ProcessEntity::getDeletedFlag, 0)
                .orderByDesc(ProcessEntity::getProcessId))
            .stream()
            .map(route -> ProcessRouteVO.from(route, listOperations(route.getProcessId())))
            .toList();
    }

    @Override
    public ProcessRouteVO getById(Long processId) {
        ProcessEntity route = getRouteOrThrow(processId);
        return ProcessRouteVO.from(route, listOperations(processId));
    }

    @Override
    @Transactional
    public ProcessRouteVO create(Long projectId, ProcessRouteSaveDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        validateOperations(dto.getOperations());
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUserName();
        ProcessEntity route = new ProcessEntity();
        route.setProductId(projectId);
        route.setProcessCode("PROC-" + projectId + "-" + System.currentTimeMillis());
        route.setProcessName(dto.getProcessName());
        route.setProcessType(TYPE_ROUTING);
        route.setVersionNo(dto.getVersionNo());
        route.setStatus(STATUS_DRAFT);
        route.setRemark(dto.getRemark());
        fillCreateAudit(route, now, operator);
        processRepository.insert(route);
        rebuildOperations(route, dto.getOperations(), now, operator);
        writeLog(OperationActionConstants.PROCESS_ROUTE_CREATE, route, product, "{\"action\":\"create\"}", request);
        return getById(route.getProcessId());
    }

    @Override
    @Transactional
    public ProcessRouteVO update(Long processId, ProcessRouteSaveDTO dto, HttpServletRequest request) {
        ProcessEntity route = requireEditableRoute(processId);
        Product product = getProductOrThrow(route.getProductId());
        validateOperations(dto.getOperations());
        route.setProcessName(dto.getProcessName());
        route.setVersionNo(dto.getVersionNo());
        route.setRemark(dto.getRemark());
        fillUpdateAudit(route);
        processRepository.updateById(route);
        softDeleteOperations(processId);
        rebuildOperations(route, dto.getOperations(), LocalDateTime.now(), currentUserName());
        writeLog(OperationActionConstants.PROCESS_ROUTE_UPDATE, route, product, "{\"action\":\"update\"}", request);
        return getById(processId);
    }

    @Override
    @Transactional
    public ProcessRouteVO freeze(Long processId, HttpServletRequest request) {
        ProcessEntity route = requireEditableRoute(processId);
        Product product = getProductOrThrow(route.getProductId());
        List<ProcessOperationVO> operations = listOperations(processId);
        if (operations.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "工艺路线至少需要1道工序才能冻结");
        }
        route.setStatus(STATUS_LOCKED);
        route.setFrozenAt(LocalDateTime.now());
        route.setFrozenBy(currentUserName());
        fillUpdateAudit(route);
        processRepository.updateById(route);
        processRepository.selectList(new LambdaQueryWrapper<ProcessEntity>()
                .eq(ProcessEntity::getParentProcessId, processId)
                .eq(ProcessEntity::getProcessType, TYPE_OPERATION)
                .eq(ProcessEntity::getDeletedFlag, 0))
            .forEach(operation -> {
                operation.setStatus(STATUS_LOCKED);
                fillUpdateAudit(operation);
                processRepository.updateById(operation);
            });
        writeLog(OperationActionConstants.PROCESS_ROUTE_FREEZE, route, product, "{\"action\":\"freeze\"}", request);
        return getById(processId);
    }

    private Product getProductOrThrow(Long projectId) {
        Product product = productRepository.selectById(projectId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return product;
    }

    private ProcessEntity getRouteOrThrow(Long processId) {
        ProcessEntity route = processRepository.selectById(processId);
        if (route == null || Integer.valueOf(1).equals(route.getDeletedFlag()) || !TYPE_ROUTING.equals(route.getProcessType())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "工艺路线不存在");
        }
        return route;
    }

    private ProcessEntity requireEditableRoute(Long processId) {
        ProcessEntity route = getRouteOrThrow(processId);
        if (STATUS_LOCKED.equals(route.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_FROZEN, "工艺路线已锁定，不能修改");
        }
        return route;
    }

    private List<ProcessOperationVO> listOperations(Long processId) {
        return processRepository.selectList(new LambdaQueryWrapper<ProcessEntity>()
                .eq(ProcessEntity::getParentProcessId, processId)
                .eq(ProcessEntity::getProcessType, TYPE_OPERATION)
                .eq(ProcessEntity::getDeletedFlag, 0)
                .orderByAsc(ProcessEntity::getSequenceNo))
            .stream()
            .map(ProcessOperationVO::from)
            .toList();
    }

    private void validateOperations(List<ProcessOperationDTO> operations) {
        if (operations == null || operations.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "工艺路线至少需要1道工序");
        }
        Set<Integer> sequenceNos = new HashSet<>();
        for (ProcessOperationDTO operation : operations) {
            if (operation.getSequenceNo() == null || operation.getSequenceNo() <= 0) {
                throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "工序顺序必须大于0");
            }
            if (!sequenceNos.add(operation.getSequenceNo())) {
                throw new BusinessException(ErrorCodeConstants.CODE_CONFLICT, "同一工艺路线下工序顺序不能重复");
            }
            if (!StringUtils.hasText(operation.getProcessName()) || !StringUtils.hasText(operation.getQualityRequirement())) {
                throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "工序名称和质量要求不能为空");
            }
            if (StringUtils.hasText(operation.getProcessParamJson())) {
                try {
                    objectMapper.readTree(operation.getProcessParamJson());
                } catch (Exception ex) {
                    throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "工艺参数必须是合法JSON");
                }
            }
        }
    }

    private void rebuildOperations(ProcessEntity route, List<ProcessOperationDTO> operations, LocalDateTime now, String operator) {
        for (ProcessOperationDTO dto : operations) {
            ProcessEntity operation = new ProcessEntity();
            operation.setParentProcessId(route.getProcessId());
            operation.setProductId(route.getProductId());
            operation.setProcessCode(route.getProcessCode() + "-OP-" + dto.getSequenceNo());
            operation.setProcessName(dto.getProcessName());
            operation.setProcessType(TYPE_OPERATION);
            operation.setVersionNo(route.getVersionNo());
            operation.setSequenceNo(dto.getSequenceNo());
            operation.setProcessParamJson(dto.getProcessParamJson());
            operation.setStandardTimeMins(dto.getStandardTimeMins());
            operation.setQualityRequirement(dto.getQualityRequirement());
            operation.setStatus(STATUS_DRAFT);
            operation.setRemark(dto.getRemark());
            fillCreateAudit(operation, now, operator);
            processRepository.insert(operation);
        }
    }

    private void softDeleteOperations(Long processId) {
        processRepository.selectList(new LambdaQueryWrapper<ProcessEntity>()
                .eq(ProcessEntity::getParentProcessId, processId)
                .eq(ProcessEntity::getProcessType, TYPE_OPERATION)
                .eq(ProcessEntity::getDeletedFlag, 0))
            .forEach(operation -> {
                operation.setDeletedFlag(1);
                fillUpdateAudit(operation);
                processRepository.updateById(operation);
            });
    }

    private void fillCreateAudit(com.yuewei.plm.repository.entity.BaseEntity entity, LocalDateTime now, String operator) {
        entity.setCreatedAt(now);
        entity.setCreatedBy(operator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        entity.setDeletedFlag(0);
    }

    private void fillUpdateAudit(com.yuewei.plm.repository.entity.BaseEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(currentUserName());
    }

    private String currentUserName() {
        return CurrentUserContext.get()
            .map(CurrentUser::displayName)
            .filter(StringUtils::hasText)
            .orElse("system");
    }

    private void writeLog(String action, ProcessEntity route, Product product, String detailJson, HttpServletRequest request) {
        operationLogService.logSuccess(OperationLogCreateCommand.builder()
            .action(action)
            .businessType("PROCESS")
            .businessId(String.valueOf(route.getProcessId()))
            .businessCode(route.getProcessCode())
            .businessName(route.getProcessName())
            .detailJson(detailJson)
            .request(request)
            .build());
    }
}
