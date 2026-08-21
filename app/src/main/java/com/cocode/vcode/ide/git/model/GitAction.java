package com.cocode.vcode.ide.git.model;

/**
 * Actions supported by the Git dashboard and commit management UI.
 */
public enum GitAction {
    SOFT_RESET,
    HARD_RESET,
    MIXED_RESET,
    CHERRY_PICK,
    REVERT_COMMIT,
    STASH,
    STASH_POP
}