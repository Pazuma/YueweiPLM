package com.yuewei.plm.module.attachment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuewei.plm.common.config.AppProperties;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.common.util.RequestIdUtil;
import com.yuewei.plm.common.vo.PageVO;
import com.yuewei.plm.infrastructure.storage.StorageClient;
import com.yuewei.plm.infrastructure.storage.StorageClient.StoredFile;
import com.yuewei.plm.module.attachment.constant.AttachmentOwnerTypeConstants;
import com.yuewei.plm.module.attachment.dto.AttachmentQueryDTO;
import com.yuewei.plm.module.attachment.entity.Attachment;
import com.yuewei.plm.module.attachment.entity.AttachmentDownloadLog;
import com.yuewei.plm.module.attachment.repository.AttachmentDownloadLogRepository;
import com.yuewei.plm.module.attachment.repository.AttachmentRepository;
import com.yuewei.plm.module.attachment.service.AttachmentDownloadResource;
import com.yuewei.plm.module.attachment.service.AttachmentService;
import com.yuewei.plm.module.attachment.vo.AttachmentVO;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final String STORAGE_LOCAL = "local";
    private static final String STATUS_DRAFT = "draft";
    private static final Set<String> CATEGORIES = Set.of(
        "sop", "sip", "testing", "drawing", "customer_confirm", "other"
    );

    private final ProductRepository productRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentDownloadLogRepository attachmentDownloadLogRepository;
    private final TimelineDefinitionProvider timelineDefinitionProvider;
    private final StorageClient storageClient;
    private final AppProperties appProperties;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public AttachmentVO uploadTimelineAttachment(Long projectId, String nodeKey, MultipartFile file, String fileCategory,
                                                 String versionNo, String remark, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        requireValidNode(product, nodeKey);
        requireValidUpload(file, fileCategory);
        String originalName = file.getOriginalFilename();
        String ext = extension(originalName);
        StoredFile storedFile = storageClient.store("projects/" + projectId + "/" + nodeKey, file);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUserName();
        Attachment attachment = new Attachment();
        attachment.setOwnerObjectType(AttachmentOwnerTypeConstants.PRODUCT);
        attachment.setOwnerObjectId(projectId);
        attachment.setTimelineNodeKey(nodeKey);
        attachment.setFileCategory(fileCategory);
        attachment.setFileName(originalName);
        attachment.setOriginalFileName(originalName);
        attachment.setFileExt(ext);
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(storedFile.fileSize());
        attachment.setChecksum(storedFile.checksum());
        attachment.setStorageType(STORAGE_LOCAL);
        attachment.setStorageKey(storedFile.storageKey());
        attachment.setVersionNo(StringUtils.hasText(versionNo) ? versionNo : "V1");
        attachment.setStatus(STATUS_DRAFT);
        attachment.setRemark(remark);
        fillCreateAudit(attachment, now, operator);
        attachmentRepository.insert(attachment);
        writeLog(OperationActionConstants.ATTACHMENT_UPLOAD, attachment, "{\"action\":\"upload\"}", request);
        return AttachmentVO.from(attachment);
    }

    @Override
    public java.util.List<AttachmentVO> listTimelineAttachments(Long projectId, String nodeKey) {
        Product product = getProductOrThrow(projectId);
        requireValidNode(product, nodeKey);
        return attachmentRepository.selectList(baseQuery()
                .eq(Attachment::getOwnerObjectId, projectId)
                .eq(Attachment::getTimelineNodeKey, nodeKey)
                .orderByDesc(Attachment::getCreatedAt))
            .stream()
            .map(AttachmentVO::from)
            .toList();
    }

    @Override
    public PageVO<AttachmentVO> pageFileCenter(AttachmentQueryDTO queryDTO) {
        long pageNo = queryDTO.getPage() == null || queryDTO.getPage() < 1 ? 1 : queryDTO.getPage();
        long pageSize = queryDTO.getSize() == null || queryDTO.getSize() < 1 ? 20 : Math.min(queryDTO.getSize(), 100);
        LambdaQueryWrapper<Attachment> wrapper = baseQuery()
            .eq(queryDTO.getProjectId() != null, Attachment::getOwnerObjectId, queryDTO.getProjectId())
            .eq(StringUtils.hasText(queryDTO.getNodeKey()), Attachment::getTimelineNodeKey, queryDTO.getNodeKey())
            .eq(StringUtils.hasText(queryDTO.getFileCategory()), Attachment::getFileCategory, queryDTO.getFileCategory())
            .and(StringUtils.hasText(queryDTO.getKeyword()), w -> w
                .like(Attachment::getFileName, queryDTO.getKeyword())
                .or()
                .like(Attachment::getRemark, queryDTO.getKeyword()))
            .orderByDesc(Attachment::getCreatedAt);
        IPage<Attachment> page = attachmentRepository.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageVO.<AttachmentVO>builder()
            .content(page.getRecords().stream().map(AttachmentVO::from).toList())
            .page(page.getCurrent())
            .size(page.getSize())
            .totalElements(page.getTotal())
            .totalPages(page.getPages())
            .build();
    }

    @Override
    public AttachmentVO getById(Long attachmentId) {
        return AttachmentVO.from(getAttachmentOrThrow(attachmentId));
    }

    @Override
    @Transactional
    public AttachmentDownloadResource download(Long attachmentId, HttpServletRequest request) {
        Attachment attachment = getAttachmentOrThrow(attachmentId);
        Resource resource = storageClient.loadAsResource(attachment.getStorageKey());
        writeDownloadLog(attachment, request);
        writeLog(OperationActionConstants.ATTACHMENT_DOWNLOAD, attachment, "{\"action\":\"download\"}", request);
        return new AttachmentDownloadResource(attachment.getOriginalFileName(), attachment.getContentType(), resource);
    }

    @Override
    @Transactional
    public void delete(Long attachmentId, HttpServletRequest request) {
        Attachment attachment = getAttachmentOrThrow(attachmentId);
        attachment.setDeletedFlag(1);
        fillUpdateAudit(attachment);
        attachmentRepository.updateById(attachment);
        writeLog(OperationActionConstants.ATTACHMENT_DELETE, attachment, "{\"action\":\"delete\"}", request);
    }

    private LambdaQueryWrapper<Attachment> baseQuery() {
        return new LambdaQueryWrapper<Attachment>()
            .eq(Attachment::getOwnerObjectType, AttachmentOwnerTypeConstants.PRODUCT)
            .eq(Attachment::getDeletedFlag, 0);
    }

    private Product getProductOrThrow(Long projectId) {
        Product product = productRepository.selectById(projectId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return product;
    }

    private Attachment getAttachmentOrThrow(Long attachmentId) {
        Attachment attachment = attachmentRepository.selectById(attachmentId);
        if (attachment == null || Integer.valueOf(1).equals(attachment.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.ATTACHMENT_NOT_FOUND, "附件不存在");
        }
        return attachment;
    }

    private void requireValidNode(Product product, String nodeKey) {
        boolean exists = timelineDefinitionProvider.getDefinitions(product.getProductType())
            .stream()
            .anyMatch(definition -> definition.nodeCode().equals(nodeKey));
        if (!exists) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "时间轴节点不存在");
        }
    }

    private void requireValidUpload(MultipartFile file, String fileCategory) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "上传文件不能为空");
        }
        if (!StringUtils.hasText(fileCategory) || !CATEGORIES.contains(fileCategory)) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "文件分类不支持");
        }
        if (file.getSize() > appProperties.getStorage().getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCodeConstants.FILE_SIZE_EXCEEDED, "文件大小超过限制");
        }
        String ext = extension(file.getOriginalFilename());
        Set<String> allowed = Arrays.stream(appProperties.getStorage().getAllowedExtensions().split(","))
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
        if (!allowed.contains(ext)) {
            throw new BusinessException(ErrorCodeConstants.FILE_TYPE_NOT_SUPPORTED, "文件类型不支持");
        }
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void writeDownloadLog(Attachment attachment, HttpServletRequest request) {
        CurrentUser user = CurrentUserContext.get().orElse(null);
        String operator = currentUserName();
        AttachmentDownloadLog log = new AttachmentDownloadLog();
        log.setAttachmentId(attachment.getAttachmentId());
        log.setOperatorUserId(user == null ? null : user.userId());
        log.setOperatorUserName(user == null ? null : user.displayName());
        log.setRequestId(RequestIdUtil.getRequestId(request));
        log.setClientIp(request == null ? null : request.getRemoteAddr());
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        fillCreateAudit(log, LocalDateTime.now(), operator);
        attachmentDownloadLogRepository.insert(log);
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

    private void writeLog(String action, Attachment attachment, String detailJson, HttpServletRequest request) {
        operationLogService.logSuccess(OperationLogCreateCommand.builder()
            .action(action)
            .businessType("ATTACHMENT")
            .businessId(String.valueOf(attachment.getAttachmentId()))
            .businessCode(attachment.getStorageKey())
            .businessName(attachment.getOriginalFileName())
            .detailJson(detailJson)
            .request(request)
            .build());
    }
}
