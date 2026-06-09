package com.yuewei.plm.common.constant;

public final class ErrorCodeConstants {

    public static final int SUCCESS = 0;
    public static final int VALIDATION_ERROR = 40001;
    public static final int STATUS_TRANSITION_ILLEGAL = 40002;
    public static final int UNAUTHORIZED = 40101;
    public static final int FORBIDDEN = 40103;
    public static final int VERSION_FROZEN = 40301;
    public static final int VERSION_RELEASED = 40302;
    public static final int RESOURCE_NOT_FOUND = 40401;
    public static final int CODE_CONFLICT = 40901;
    public static final int INTERNAL_ERROR = 50001;

    private ErrorCodeConstants() {
    }
}
