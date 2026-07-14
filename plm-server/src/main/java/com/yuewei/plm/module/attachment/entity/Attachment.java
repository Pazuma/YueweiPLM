package com.yuewei.plm.module.attachment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuewei.plm.repository.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("plm_attachment")
@EqualsAndHashCode(callSuper = true)
public class Attachment extends BaseEntity {

    @TableId(value = "attachment_id", type = IdType.AUTO)
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
}
