package com.yuewei.plm.module.project.constant;

import java.util.List;

public final class TimelineNodeConstants {

    public static final String PRODUCT_TYPE_PRODUCT_LINE = "product_line";
    public static final String PRODUCT_TYPE_MODEL_VARIANT = "model_variant";

    public static final String NODE_STATUS_COMPLETED = "completed";
    public static final String NODE_STATUS_CURRENT = "current";
    public static final String NODE_STATUS_PENDING = "pending";

    public static final String PRODUCT_TYPE_NAME_PRODUCT_LINE = "新产品线";
    public static final String PRODUCT_TYPE_NAME_MODEL_VARIANT = "新型号线";

    public static final List<TimelineNodeDefinition> PRODUCT_LINE_NODES = List.of(
        new TimelineNodeDefinition(1, "PRODUCT_LINE_INIT_CONFIRM", "立项确认"),
        new TimelineNodeDefinition(2, "PRODUCT_LINE_DESIGN_CONFIRM", "设计确认"),
        new TimelineNodeDefinition(3, "PRODUCT_LINE_MOLD_TRIAL", "开模试模"),
        new TimelineNodeDefinition(4, "PRODUCT_LINE_SAMPLE_PROCESS", "样品与工艺"),
        new TimelineNodeDefinition(5, "PRODUCT_LINE_SMALL_BATCH_MX", "小批与 MX 验证"),
        new TimelineNodeDefinition(6, "PRODUCT_LINE_PRODUCTION_DECISION", "投产决策")
    );

    public static final List<TimelineNodeDefinition> MODEL_VARIANT_NODES = List.of(
        new TimelineNodeDefinition(1, "MODEL_VARIANT_EXTENSION_CONFIRM", "扩展确认"),
        new TimelineNodeDefinition(2, "MODEL_VARIANT_DIFF_DESIGN", "差异设计"),
        new TimelineNodeDefinition(3, "MODEL_VARIANT_MOLD_JUDGEMENT", "模具判断"),
        new TimelineNodeDefinition(4, "MODEL_VARIANT_DIFF_VERIFY", "差异验证"),
        new TimelineNodeDefinition(5, "MODEL_VARIANT_SMALL_BATCH_MX", "小批与 MX 验证"),
        new TimelineNodeDefinition(6, "MODEL_VARIANT_FREEZE_RELEASE", "冻结发布")
    );

    private TimelineNodeConstants() {
    }

    public record TimelineNodeDefinition(Integer stepNo, String nodeCode, String nodeName) {
    }
}
