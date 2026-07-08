package com.manganovel.common;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Result<T> {

    public static final int CODE_SUCCESS = 200;
    public static final int CODE_ERROR = 500;

    private final int code;
    private final String message;
    private final T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_SUCCESS, "success", data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return fail(CODE_ERROR, message);
    }
}
