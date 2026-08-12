package com.yuewei.plm.module.code.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CodeItemSaveDTO {
    @NotBlank
    private String codeType;
    @NotBlank
    private String codeValue;
    @NotBlank
    private String codeName;
    private String codeNameZh;
    @NotNull
    @Min(0)
    private Integer sortOrder;
}
