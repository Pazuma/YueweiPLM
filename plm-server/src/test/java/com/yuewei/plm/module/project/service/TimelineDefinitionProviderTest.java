package com.yuewei.plm.module.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import org.junit.jupiter.api.Test;

class TimelineDefinitionProviderTest {

    private final TimelineDefinitionProvider provider = new TimelineDefinitionProvider();

    @Test
    void returnsProductLineDefinitionsInFixedOrder() {
        var definitions = provider.getDefinitions(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE);

        assertThat(definitions).hasSize(6);
        assertThat(definitions.get(0).stepNo()).isEqualTo(1);
        assertThat(definitions.get(0).nodeCode()).isEqualTo("PRODUCT_LINE_INIT_CONFIRM");
        assertThat(definitions.get(0).nodeName()).isEqualTo("立项确认");
        assertThat(definitions.get(5).stepNo()).isEqualTo(6);
        assertThat(definitions.get(5).nodeName()).isEqualTo("投产决策");
    }

    @Test
    void returnsModelVariantDefinitionsInFixedOrder() {
        var definitions = provider.getDefinitions(TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT);

        assertThat(definitions).hasSize(6);
        assertThat(definitions.get(0).nodeCode()).isEqualTo("MODEL_VARIANT_EXTENSION_CONFIRM");
        assertThat(definitions.get(0).nodeName()).isEqualTo("扩展确认");
        assertThat(definitions.get(5).nodeCode()).isEqualTo("MODEL_VARIANT_FREEZE_RELEASE");
        assertThat(definitions.get(5).nodeName()).isEqualTo("冻结发布");
    }

    @Test
    void clampsCurrentNodeNameToKnownRange() {
        assertThat(provider.getCurrentNodeName(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE, -1))
            .isEqualTo("立项确认");
        assertThat(provider.getCurrentNodeName(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE, 99))
            .isEqualTo("投产决策");
    }

    @Test
    void rejectsUnsupportedProductType() {
        assertThatThrownBy(() -> provider.getDefinitions("unknown"))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.VALIDATION_ERROR);
    }
}
