package com.cocode.vcode.ide.git.core;

import androidx.annotation.NonNull;

/**
 * Represents the outcome of a Git operation (status and message).
 */
public class GitOperationResult {

    private final Status status;
    private final String message;

    private GitOperationResult(Status status, String message) {
        this.status = status;
        this.message = message != null ? message : "";
    }

    public static GitOperationResult success(String message) {
        return new GitOperationResult(Status.SUCCESS, message);
    }

    public static GitOperationResult error(String message) {
        return new GitOperationResult(Status.ERROR, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    @NonNull
    @Override
    public String toString() {
        return "GitOperationResult{" + status + ", " + message + "}";
    }

    public enum Status {SUCCESS, ERROR, CONFLICT}
}