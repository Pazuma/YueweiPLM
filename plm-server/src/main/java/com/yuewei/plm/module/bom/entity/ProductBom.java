package com.yuewei.plm.module.bom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuewei.plm.repository.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("plm_product_bom")
@EqualsAndHashCode(callSuper = true)
public class ProductBom extends BaseEntity {

    @TableId(value = "product_bom_id", type = IdType.AUTO)
    private Long productBomId;
    private Long productId;
    private String bomCode;
    private String bomName;
    private String bomType;
    private String versionNo;
    private String status;
    private LocalDateTime frozenAt;
    private String frozenBy;
    private String remark;
}
