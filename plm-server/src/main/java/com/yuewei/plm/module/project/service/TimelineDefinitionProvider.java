package com.yuewei.plm.module.project.service;

import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants.TimelineNodeDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TimelineDefinitionProvider {

    public List<TimelineNodeDefinition> getDefinitions(String productType) {
        if (TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE.equals(productType)) {
            return TimelineNodeConstants.PRODUCT_LINE_NODES;
        }
        if (TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT.equals(productType)) {
            return TimelineNodeConstants.MODEL_VARIANT_NODES;
        }
        throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "不支持的产品类型");
    }

    public String getCurrentNodeName(String productType, Integer currentStepNo) {
        List<TimelineNodeDefinition> definitions = getDefinitions(productType);
        int normalizedStepNo = normalizeStepNo(currentStepNo, definitions.size());
        return definitions.get(normalizedStepNo - 1).nodeName();
    }

    private int normalizeStepNo(Integer currentStepNo, int maxStepNo) {
        if (currentStepNo == null || currentStepNo < 1) {
            return 1;
        }
        return Math.min(currentStepNo, maxStepNo);
    }
}
