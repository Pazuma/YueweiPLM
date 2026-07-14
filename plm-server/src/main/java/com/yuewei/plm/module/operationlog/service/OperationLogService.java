package com.yuewei.plm.module.operationlog.service;

import com.yuewei.plm.common.vo.PageVO;
import com.yuewei.plm.module.operationlog.dto.OperationLogQueryDTO;
import com.yuewei.plm.module.operationlog.vo.OperationLogVO;

public interface OperationLogService {

    Long logSuccess(OperationLogCreateCommand command);

    PageVO<OperationLogVO> page(OperationLogQueryDTO queryDTO);
}
