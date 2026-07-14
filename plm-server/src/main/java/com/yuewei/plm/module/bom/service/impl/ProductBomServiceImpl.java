package com.yuewei.plm.module.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.module.bom.dto.ProductBomCreateDTO;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.dto.ProductBomUpdateDTO;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomItem;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.service.ProductBomService;
import com.yuewei.plm.module.bom.vo.ProductBomItemVO;
import com.yuewei.plm.module.bom.vo.ProductBomVO;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductBomServiceImpl implements ProductBomService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_FROZEN = "frozen";

    private final ProductRepository productRepository;
    private final ProductBomRepository productBomRepository;
    private final ProductBomItemRepository productBomItemRepository;
    private final OperationLogService operationLogService;

    @Override
    public List<ProductBomVO> listByProject(Long projectId) {
        getProductOrThrow(projectId);
        return productBomRepository.selectList(new LambdaQueryWrapper<ProductBom>()
                .eq(ProductBom::getProductId, projectId)
                .eq(ProductBom::getDeletedFlag, 0)
                .orderByDesc(ProductBom::getProductBomId))
            .stream()
            .map(bom -> ProductBomVO.from(bom, listItems(bom.getProductBomId())))
            .toList();
    }

    @Override
    public ProductBomVO getById(Long bomId) {
        ProductBom bom = getBomOrThrow(bomId);
        return ProductBomVO.from(bom, listItems(bom.getProductBomId()));
    }

    @Override
    @Transactional
    public ProductBomVO create(Long projectId, ProductBomCreateDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUserName();
        ProductBom bom = new ProductBom();
        bom.setProductId(projectId);
        bom.setBomCode("BOM-" + projectId + "-" + System.currentTimeMillis());
        bom.setBomName(dto.getBomName());
        bom.setBomType(dto.getBomType());
        bom.setVersionNo(dto.getVersionNo());
        bom.setStatus(STATUS_DRAFT);
        bom.setRemark(dto.getRemark());
        fillCreateAudit(bom, now, operator);
        productBomRepository.insert(bom);
        writeLog(OperationActionConstants.BOM_CREATE, bom, product, "{\"action\":\"create\"}", request);
        return getById(bom.getProductBomId());
    }

    @Override
    @Transactional
    public ProductBomVO update(Long bomId, ProductBomUpdateDTO dto, HttpServletRequest request) {
        ProductBom bom = requireEditableBom(bomId);
        Product product = getProductOrThrow(bom.getProductId());
        bom.setBomName(dto.getBomName());
        bom.setBomType(dto.getBomType());
        bom.setVersionNo(dto.getVersionNo());
        bom.setRemark(dto.getRemark());
        fillUpdateAudit(bom);
        productBomRepository.updateById(bom);
        writeLog(OperationActionConstants.BOM_UPDATE, bom, product, "{\"action\":\"update\"}", request);
        return getById(bomId);
    }

    @Override
    @Transactional
    public ProductBomVO addItem(Long bomId, ProductBomItemDTO dto, HttpServletRequest request) {
        ProductBom bom = requireEditableBom(bomId);
        Product product = getProductOrThrow(bom.getProductId());
        requireValidItem(dto);
        requireUniqueLineNo(bomId, dto.getLineNo(), null);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUserName();
        ProductBomItem item = new ProductBomItem();
        applyItem(dto, item);
        item.setProductBomId(bomId);
        item.setProductId(bom.getProductId());
        item.setVersionNo(bom.getVersionNo());
        item.setStatus(STATUS_DRAFT);
        fillCreateAudit(item, now, operator);
        productBomItemRepository.insert(item);
        writeLog(OperationActionConstants.BOM_ITEM_CREATE, bom, product,
            "{\"action\":\"item_create\",\"itemId\":" + item.getProductBomItemId() + "}", request);
        return getById(bomId);
    }

    @Override
    @Transactional
    public ProductBomVO updateItem(Long bomId, Long itemId, ProductBomItemDTO dto, HttpServletRequest request) {
        ProductBom bom = requireEditableBom(bomId);
        Product product = getProductOrThrow(bom.getProductId());
        requireValidItem(dto);
        ProductBomItem item = getItemOrThrow(bomId, itemId);
        requireUniqueLineNo(bomId, dto.getLineNo(), itemId);
        applyItem(dto, item);
        fillUpdateAudit(item);
        productBomItemRepository.updateById(item);
        writeLog(OperationActionConstants.BOM_ITEM_UPDATE, bom, product,
            "{\"action\":\"item_update\",\"itemId\":" + itemId + "}", request);
        return getById(bomId);
    }

    @Override
    @Transactional
    public ProductBomVO deleteItem(Long bomId, Long itemId, HttpServletRequest request) {
        ProductBom bom = requireEditableBom(bomId);
        Product product = getProductOrThrow(bom.getProductId());
        ProductBomItem item = getItemOrThrow(bomId, itemId);
        item.setDeletedFlag(1);
        fillUpdateAudit(item);
        productBomItemRepository.updateById(item);
        writeLog(OperationActionConstants.BOM_ITEM_DELETE, bom, product,
            "{\"action\":\"item_delete\",\"itemId\":" + itemId + "}", request);
        return getById(bomId);
    }

    @Override
    @Transactional
    public ProductBomVO freeze(Long bomId, HttpServletRequest request) {
        ProductBom bom = requireEditableBom(bomId);
        Product product = getProductOrThrow(bom.getProductId());
        if (listItems(bomId).isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "BOM至少需要1条明细才能冻结");
        }
        bom.setStatus(STATUS_FROZEN);
        bom.setFrozenAt(LocalDateTime.now());
        bom.setFrozenBy(currentUserName());
        fillUpdateAudit(bom);
        productBomRepository.updateById(bom);
        writeLog(OperationActionConstants.BOM_FREEZE, bom, product, "{\"action\":\"freeze\"}", request);
        return getById(bomId);
    }

    private Product getProductOrThrow(Long projectId) {
        Product product = productRepository.selectById(projectId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return product;
    }

    private ProductBom getBomOrThrow(Long bomId) {
        ProductBom bom = productBomRepository.selectById(bomId);
        if (bom == null || Integer.valueOf(1).equals(bom.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "BOM不存在");
        }
        return bom;
    }

    private ProductBom requireEditableBom(Long bomId) {
        ProductBom bom = getBomOrThrow(bomId);
        if (STATUS_FROZEN.equals(bom.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_FROZEN, "BOM已冻结，不能修改");
        }
        return bom;
    }

    private ProductBomItem getItemOrThrow(Long bomId, Long itemId) {
        ProductBomItem item = productBomItemRepository.selectById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getDeletedFlag()) || !bomId.equals(item.getProductBomId())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "BOM明细不存在");
        }
        return item;
    }

    private List<ProductBomItemVO> listItems(Long bomId) {
        return productBomItemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
                .eq(ProductBomItem::getProductBomId, bomId)
                .eq(ProductBomItem::getDeletedFlag, 0)
                .orderByAsc(ProductBomItem::getLineNo))
            .stream()
            .map(ProductBomItemVO::from)
            .toList();
    }

    private void requireUniqueLineNo(Long bomId, Integer lineNo, Long currentItemId) {
        Long count = productBomItemRepository.selectCount(new LambdaQueryWrapper<ProductBomItem>()
            .eq(ProductBomItem::getProductBomId, bomId)
            .eq(ProductBomItem::getLineNo, lineNo)
            .eq(ProductBomItem::getDeletedFlag, 0)
            .ne(currentItemId != null, ProductBomItem::getProductBomItemId, currentItemId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCodeConstants.CODE_CONFLICT, "同一BOM下行号不能重复");
        }
    }

    private void requireValidItem(ProductBomItemDTO dto) {
        if (dto.getQuantity() == null || dto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "BOM明细用量必须大于0");
        }
        if (dto.getLineNo() == null || dto.getLineNo() <= 0) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "BOM明细行号必须大于0");
        }
        if (!StringUtils.hasText(dto.getItemName()) || !StringUtils.hasText(dto.getUnit())) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "BOM明细名称和单位不能为空");
        }
    }

    private void applyItem(ProductBomItemDTO dto, ProductBomItem item) {
        item.setInventoryId(dto.getInventoryId());
        item.setItemCode(dto.getItemCode());
        item.setItemName(dto.getItemName());
        item.setSpecification(dto.getSpecification());
        item.setLineNo(dto.getLineNo());
        item.setQuantity(dto.getQuantity());
        item.setUnit(dto.getUnit());
        item.setLossRate(dto.getLossRate() == null ? BigDecimal.ZERO : dto.getLossRate());
        item.setSubstituteFlag(dto.getSubstituteFlag() == null ? 0 : dto.getSubstituteFlag());
        item.setRemark(dto.getRemark());
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

    private void writeLog(String action, ProductBom bom, Product product, String detailJson, HttpServletRequest request) {
        operationLogService.logSuccess(OperationLogCreateCommand.builder()
            .action(action)
            .businessType("PRODUCT_BOM")
            .businessId(String.valueOf(bom.getProductBomId()))
            .businessCode(bom.getBomCode())
            .businessName(bom.getBomName())
            .detailJson(detailJson)
            .request(request)
            .build());
    }
}
