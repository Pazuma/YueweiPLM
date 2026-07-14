package com.yuewei.plm.module.project.service.impl;

import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.module.attachment.constant.AttachmentOwnerTypeConstants;
import com.yuewei.plm.module.attachment.entity.Attachment;
import com.yuewei.plm.module.attachment.repository.AttachmentRepository;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants.TimelineNodeDefinition;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.module.project.service.TimelineService;
import com.yuewei.plm.module.project.vo.TimelineDetailVO;
import com.yuewei.plm.module.project.vo.TimelineNodeVO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    private final ProductRepository productRepository;
    private final TimelineDefinitionProvider timelineDefinitionProvider;
    private final AttachmentRepository attachmentRepository;

    @Override
    public TimelineDetailVO getTimeline(Long projectId) {
        Product product = getProductOrThrow(projectId);
        List<TimelineNodeDefinition> definitions = timelineDefinitionProvider.getDefinitions(product.getProductType());
        int currentStepNo = normalizeStepNo(product.getCurrentStepNo(), definitions.size());
        TimelineNodeDefinition currentNode = definitions.get(currentStepNo - 1);
        return TimelineDetailVO.builder()
            .projectId(product.getProductId())
            .productId(product.getProductId())
            .productType(product.getProductType())
            .currentStepNo(currentStepNo)
            .currentConfirmed(isCurrentNodeConfirmed(product, currentNode))
            .confirmedNodeKey(product.getTimelineConfirmedNodeKey())
            .lastAction(product.getTimelineLastAction())
            .lastReason(product.getTimelineLastReason())
            .lastOperatedAt(product.getTimelineLastOperatedAt())
            .lastOperatorUserId(product.getTimelineLastOperatorUserId())
            .lastOperatorUserName(product.getTimelineLastOperatorUserName())
            .nodes(definitions.stream()
                .map(definition -> toNodeVO(definition, currentStepNo, product.getProductId(), product))
                .toList())
            .build();
    }

    private TimelineNodeVO toNodeVO(TimelineNodeDefinition definition, int currentStepNo, Long productId, Product product) {
        return TimelineNodeVO.builder()
            .stepNo(definition.stepNo())
            .nodeCode(definition.nodeCode())
            .nodeName(definition.nodeName())
            .nodeStatus(resolveNodeStatus(definition.stepNo(), currentStepNo))
            .documentCount(countDocuments(productId, definition.nodeCode()))
            .confirmed(isCurrentNodeConfirmed(product, definition))
            .build();
    }

    private boolean isCurrentNodeConfirmed(Product product, TimelineNodeDefinition definition) {
        return Boolean.TRUE.equals(product.getTimelineCurrentConfirmed())
            && definition.nodeCode().equals(product.getTimelineConfirmedNodeKey());
    }

    private String resolveNodeStatus(int stepNo, int currentStepNo) {
        if (stepNo < currentStepNo) {
            return TimelineNodeConstants.NODE_STATUS_COMPLETED;
        }
        if (stepNo == currentStepNo) {
            return TimelineNodeConstants.NODE_STATUS_CURRENT;
        }
        return TimelineNodeConstants.NODE_STATUS_PENDING;
    }

    private int countDocuments(Long productId, String nodeCode) {
        Long count = attachmentRepository.selectCount(new LambdaQueryWrapper<Attachment>()
            .eq(Attachment::getOwnerObjectType, AttachmentOwnerTypeConstants.PRODUCT)
            .eq(Attachment::getOwnerObjectId, productId)
            .eq(Attachment::getTimelineNodeKey, nodeCode)
            .eq(Attachment::getDeletedFlag, 0));
        return count == null ? 0 : count.intValue();
    }

    private int normalizeStepNo(Integer currentStepNo, int maxStepNo) {
        if (currentStepNo == null || currentStepNo < 1) {
            return 1;
        }
        return Math.min(currentStepNo, maxStepNo);
    }

    private Product getProductOrThrow(Long projectId) {
        Product product = productRepository.selectById(projectId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return product;
    }
}
