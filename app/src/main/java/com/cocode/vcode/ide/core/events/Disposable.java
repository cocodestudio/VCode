package com.cocode.vcode.ide.core.events;

/**
 * Handle representing an active subscription or resource that can be released.
 */
@FunctionalInterface
public interface Disposable {

    /**
     * Cancels the subscription and removes the registered listener.
     */
    void dispose();
}
