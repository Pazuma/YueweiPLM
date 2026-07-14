package com.yuewei.plm.module.attachment.vo;

import com.yuewei.plm.module.attachment.entity.Attachment;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttachmentVO {

    private Long attachmentId;
    private String ownerObjectType;
    private Long ownerObjectId;
    private String timelineNodeKey;
    private String fileCategory;
    private String fileName;
    private String originalFileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String checksum;
    private String storageType;
    private String storageKey;
    private String versionNo;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private String createdBy;

    public static AttachmentVO from(Attachment attachment) {
        return AttachmentVO.builder()
            .attachmentId(attachment.getAttachmentId())
            .ownerObjectType(attachment.getOwnerObjectType())
            .ownerObjectId(attachment.getOwnerObjectId())
            .timelineNodeKey(attachment.getTimelineNodeKey())
            .fileCategory(attachment.getFileCategory())
            .fileName(attachment.getFileName())
            .originalFileName(attachment.getOriginalFileName())
            .fileExt(attachment.getFileExt())
            .contentType(attachment.getContentType())
            .fileSize(attachment.getFileSize())
            .checksum(attachment.getChecksum())
            .storageType(attachment.getStorageType())
            .storageKey(attachment.getStorageKey())
            .versionNo(attachment.getVersionNo())
            .status(attachment.getStatus())
            .remark(attachment.getRemark())
            .createdAt(attachment.getCreatedAt())
            .createdBy(attachment.getCreatedBy())
            .build();
    }
}
