package com.diladrive.common;

/** Standard envelope for every API response: { success, message, data, errors }. */
public record ApiResponse<T>(boolean success, String message, T data, Object errors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static ApiResponse<Void> error(String message, Object errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}
