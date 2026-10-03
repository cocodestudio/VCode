package com.cocode.vcode.ide.core.events;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Centralized, thread-safe publish-subscribe event bus for the VCode IDE.
 * Allows decoupling document lifecycles, project mutations, and extension hooks
 * without hardcoded compile-time dependencies or leaky Activity/ViewModel references.
 */
public final class IdeEventBus {

    public enum ThreadMode {
        /**
         * Dispatches the event immediately on the thread that published it.
         */
        POSTING,

        /**
         * Dispatches the event on the Android Main (UI) thread.
         */
        MAIN,

        /**
         * Dispatches the event on a background worker thread via {@link ExecutorProvider#runOnCpu(Runnable)}.
         */
        BACKGROUND,

        /**
         * Dispatches the event on the sequential I/O thread via {@link ExecutorProvider#runOnIo(Runnable)}.
         */
        IO
    }

    private static volatile IdeEventBus sInstance;

    private final Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> subscriptions = new ConcurrentHashMap<>();

    private IdeEventBus() {
    }

    public static IdeEventBus getInstance() {
        if (sInstance == null) {
            synchronized (IdeEventBus.class) {
                if (sInstance == null) {
                    sInstance = new IdeEventBus();
                }
            }
        }
        return sInstance;
    }

    /**
     * Subscribes a listener to events of type {@code eventClass} (and its subtypes)
     * using the default {@link ThreadMode#POSTING}.
     *
     * @return a {@link Disposable} token to unsubscribe.
     */
    public <T extends IdeEvent> Disposable subscribe(@NonNull Class<T> eventClass, @NonNull Consumer<T> listener) {
        return subscribe(eventClass, ThreadMode.POSTING, listener);
    }

    /**
     * Subscribes a listener to events of type {@code eventClass} (and its subtypes)
     * dispatched on the specified {@link ThreadMode}.
     *
     * @return a {@link Disposable} token to unsubscribe.
     */
    public <T extends IdeEvent> Disposable subscribe(@NonNull Class<T> eventClass,
                                                     @NonNull ThreadMode threadMode,
                                                     @NonNull Consumer<T> listener) {
        Subscription<T> sub = new Subscription<>(eventClass, threadMode, listener);
        subscriptions.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(sub);

        return () -> {
            CopyOnWriteArrayList<Subscription<?>> list = subscriptions.get(eventClass);
            if (list != null) {
                list.remove(sub);
            }
        };
    }

    /**
     * Publishes an {@link IdeEvent} to all registered subscribers.
     * Subscribers matching the event's class or any of its superclasses/interfaces
     * will be notified according to their configured {@link ThreadMode}.
     */
    @SuppressWarnings("unchecked")
    public void publish(@NonNull IdeEvent event) {
        Class<?> eventClass = event.getClass();

        for (Map.Entry<Class<?>, CopyOnWriteArrayList<Subscription<?>>> entry : subscriptions.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                for (Subscription<?> sub : entry.getValue()) {
                    dispatchToSubscription((Subscription<IdeEvent>) sub, event);
                }
            }
        }
    }

    /**
     * Asynchronously dispatches the event on the CPU worker pool.
     */
    public void publishAsync(@NonNull IdeEvent event) {
        ExecutorProvider.getInstance().runOnCpu(() -> publish(event));
    }

    private void dispatchToSubscription(Subscription<IdeEvent> sub, IdeEvent event) {
        Runnable task = () -> {
            try {
                sub.listener.accept(event);
            } catch (Throwable t) {
                // Prevent one failing listener from crashing other listeners
            }
        };

        switch (sub.threadMode) {
            case MAIN:
                ExecutorProvider.getInstance().runOnMain(task);
                break;
            case BACKGROUND:
                ExecutorProvider.getInstance().runOnCpu(task);
                break;
            case IO:
                ExecutorProvider.getInstance().runOnIo(task);
                break;
            case POSTING:
            default:
                task.run();
                break;
        }
    }

    /**
     * Clears all subscriptions. Primarily intended for test teardowns.
     */
    public void clear() {
        subscriptions.clear();
    }

    public void clearAllSubscriptions() {
        clear();
    }

    private static class Subscription<T> {
        final Class<T> eventClass;
        final ThreadMode threadMode;
        final Consumer<T> listener;

        Subscription(Class<T> eventClass, ThreadMode threadMode, Consumer<T> listener) {
            this.eventClass = eventClass;
            this.threadMode = threadMode;
            this.listener = listener;
        }
    }
}
