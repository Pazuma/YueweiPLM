package com.yuewei.plm.module.bom.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ProductionRouteConfirmDTO {
    @Valid
    @NotEmpty(message = "请至少选择一条工艺路线")
    private List<RouteSelection> routes;

    private String remark;

    @Data
    public static class RouteSelection {
        @NotNull(message = "工艺路线不能为空")
        private Long processId;

        @NotNull(message = "正式 BOM 不能为空")
        private Long productBomId;

        @NotNull(message = "正式 BOM 路线不能为空")
        private Long productBomRouteId;

        @NotEmpty(message = "请至少选择一道投产工序")
        private List<Long> operationProcessIds;
    }
}
