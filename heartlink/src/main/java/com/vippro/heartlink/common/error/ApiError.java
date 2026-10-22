package com.vippro.heartlink.common.error;

public record ApiError(ErrorBody error) {
    public record ErrorBody(String code, String message) { }

    public static ApiError of(String code, String message) {
        return new ApiError(new ErrorBody(code, message));
    }
}
