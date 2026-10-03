package com.cocode.vcode.ide.core.events;

/**
 * Base interface for all typed events dispatched through the {@link IdeEventBus}.
 */
public interface IdeEvent {

    /**
     * Returns the epoch timestamp (in milliseconds) when the event occurred.
     */
    long getTimestamp();
}
