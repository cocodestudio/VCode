package com.cocode.vcode.ide.data.model;

import androidx.annotation.NonNull;

/**
 * Generic result wrapper for background operations, encapsulating either a successful data payload
 * or an error message.
 *
 * @param <T> the type of data returned on success
 */
public class Result<T> {

    private final T data;
    private final String errorMessage;
    private final boolean success;

    private Result(T data, String errorMessage, boolean success) {
        this.data = data;
        this.errorMessage = errorMessage;
        this.success = success;
    }

    /**
     * Creates a successful result holding the provided data.
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(data, null, true);
    }

    /**
     * Creates an error result with the specified error message.
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(null, message != null ? message : "Unknown error", false);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getError() {
        return errorMessage;
    }

    @NonNull
    @Override
    public String toString() {
        return success ? "Result.success(" + data + ")" : "Result.error(" + errorMessage + ")";
    }
}