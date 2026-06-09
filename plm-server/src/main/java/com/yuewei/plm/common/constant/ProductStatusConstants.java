package com.yuewei.plm.common.constant;

import java.util.Map;
import java.util.Set;

public final class ProductStatusConstants {

    public static final String DRAFT = "draft";
    public static final String DEVELOPING = "developing";
    public static final String REVIEWING = "reviewing";
    public static final String RELEASED = "released";
    public static final String ARCHIVED = "archived";

    public static final Map<String, Set<String>> TRANSITIONS = Map.of(
        DRAFT, Set.of(DEVELOPING, ARCHIVED),
        DEVELOPING, Set.of(REVIEWING, DRAFT, ARCHIVED),
        REVIEWING, Set.of(RELEASED, DEVELOPING, ARCHIVED),
        RELEASED, Set.of(ARCHIVED),
        ARCHIVED, Set.of()
    );

    private ProductStatusConstants() {
    }
}
