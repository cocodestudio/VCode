package com.cocode.vcode.ide.core.terminal.pty;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Native bridge providing authentic POSIX pseudo-terminal (PTY) master/slave allocation,
 * process execution, signals, and window resizing via libvcode-pty.
 */
public final class PtyBridge {

    private static volatile boolean isNativeLoaded = false;

    static {
        try {
            System.loadLibrary("vcode-pty");
            isNativeLoaded = true;
        } catch (Throwable t) {
            isNativeLoaded = false;
        }
    }

    public static boolean isNativeLoaded() {
        return isNativeLoaded;
    }

    private PtyBridge() {}

    /**
     * Represents a subprocess attached to a native PTY master.
     */
    public static class PtyProcess {
        private final int pid;
        private final int masterFd;
        private final ParcelFileDescriptor pfd;
        private final FileDescriptor fileDescriptor;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private volatile boolean isClosed = false;

        public PtyProcess(int pid, int masterFd) {
            this.pid = pid;
            this.masterFd = masterFd;
            ParcelFileDescriptor adoptedPfd = null;
            FileInputStream in = null;
            FileOutputStream out = null;
            try {
                adoptedPfd = ParcelFileDescriptor.adoptFd(masterFd);
                this.fileDescriptor = adoptedPfd.getFileDescriptor();
                in = new FileInputStream(this.fileDescriptor);
                out = new FileOutputStream(this.fileDescriptor);
                this.pfd = adoptedPfd;
                this.inputStream = in;
                this.outputStream = out;
            } catch (Throwable t) {
                if (in != null) {
                    try { in.close(); } catch (Throwable ignored) {}
                }
                if (out != null) {
                    try { out.close(); } catch (Throwable ignored) {}
                }
                if (adoptedPfd != null) {
                    try { adoptedPfd.close(); } catch (Throwable ignored) {}
                } else if (masterFd >= 0) {
                    PtyBridge.closeFd(masterFd);
                }
                if (pid > 1) {
                    PtyBridge.destroyProcess(pid);
                }
                throw new RuntimeException("Failed to initialize PtyProcess streams", t);
            }
        }

        public int getPid() {
            return pid;
        }

        public int getMasterFd() {
            return masterFd;
        }

        @NonNull
        public FileDescriptor getFileDescriptor() {
            return fileDescriptor;
        }

        @NonNull
        public InputStream getInputStream() {
            return inputStream;
        }

        @NonNull
        public OutputStream getOutputStream() {
            return outputStream;
        }

        public void setWindowSize(int rows, int cols) {
            if (!isClosed && masterFd >= 0) {
                PtyBridge.setWindowSize(masterFd, rows, cols);
            }
        }

        public void sendSignal(int signal) {
            if (pid > 1) {
                PtyBridge.sendSignal(pid, signal);
            }
        }

        public int waitFor() {
            if (pid > 1) {
                return PtyBridge.waitFor(pid);
            }
            return -1;
        }

        public synchronized void destroy() {
            if (isClosed) return;
            isClosed = true;

            if (pid > 1) {
                PtyBridge.destroyProcess(pid);
            }

            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Throwable ignored) {}

            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Throwable ignored) {}

            try {
                if (pfd != null) {
                    pfd.close();
                } else if (masterFd >= 0) {
                    PtyBridge.closeFd(masterFd);
                }
            } catch (Throwable ignored) {}

            PtyBridge.reapDeadProcesses();
        }
    }

    /**
     * Forks and executes a subprocess inside a dedicated PTY.
     *
     * @param cmd  Array of command arguments (e.g. ["/bin/sh", "-l"]).
     * @param cwd  Working directory (or null to inherit).
     * @param env  Array of environment variable strings "KEY=VALUE" (or null to inherit).
     * @param rows Initial terminal rows.
     * @param cols Initial terminal columns.
     * @return A {@link PtyProcess} instance, or null if creation failed.
     */
    @Nullable
    public static PtyProcess createSubprocess(@NonNull String[] cmd,
                                              @Nullable String cwd,
                                              @Nullable String[] env,
                                              int rows,
                                              int cols) {
        int[] result = null;
        try {
            result = createSubprocessNative(cmd, cwd, env, rows, cols);
            if (result == null || result.length < 2 || result[0] <= 1 || result[1] < 0) {
                return null;
            }
            return new PtyProcess(result[0], result[1]);
        } catch (Throwable t) {
            if (result != null && result.length >= 2) {
                int pid = result[0];
                int fd = result[1];
                if (fd >= 0) {
                    closeFd(fd);
                }
                if (pid > 1) {
                    destroyProcess(pid);
                }
            }
            return null;
        }
    }

    /**
     * Resizes the terminal window, dispatching SIGWINCH to the child process group.
     */
    public static void setWindowSize(int fd, int rows, int cols) {
        if (isNativeLoaded && fd >= 0) {
            try {
                setWindowSizeNative(fd, rows, cols);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Resizes the terminal window using a FileDescriptor.
     */
    public static void setWindowSize(@Nullable FileDescriptor fd, int rows, int cols) {
        if (fd != null && fd.valid()) {
            try {
                ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(fd);
                if (pfd != null) {
                    setWindowSize(pfd.getFd(), rows, cols);
                    pfd.close();
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Sends a POSIX signal (SIGINT=2, SIGQUIT=3, SIGTERM=15, SIGKILL=9, etc.) to the process group.
     */
    public static void sendSignal(int pid, int signal) {
        if (isNativeLoaded && pid > 1) {
            try {
                sendSignalNative(pid, signal);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Destroys the process tree (process group and process) with SIGTERM escalation to SIGKILL,
     * and reaps any resulting defunct child processes.
     */
    public static void destroyProcess(int pid) {
        if (isNativeLoaded && pid > 1) {
            try {
                destroyProcessNative(pid);
            } catch (Throwable ignored) {}

            try {
                com.cocode.vcode.ide.utils.ExecutorProvider.getInstance().runOnIo(() -> {
                    try {
                        Thread.sleep(100);
                        reapDeadProcesses();
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Waits for the process to terminate and returns its exit code.
     */
    public static int waitFor(int pid) {
        if (isNativeLoaded && pid > 1) {
            try {
                return waitForNative(pid);
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    /**
     * Closes the raw file descriptor.
     */
    public static void closeFd(int fd) {
        if (isNativeLoaded && fd >= 0) {
            try {
                closeFdNative(fd);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Closes the FileDescriptor.
     */
    public static void close(@Nullable FileDescriptor fd) {
        if (fd != null && fd.valid()) {
            try {
                ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(fd);
                if (pfd != null) {
                    closeFd(pfd.getFd());
                    pfd.close();
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Non-blocking reap of any zombie or defunct child processes in the OS process table.
     */
    public static void reapDeadProcesses() {
        if (isNativeLoaded) {
            try {
                reapZombiesNative();
            } catch (Throwable ignored) {}
        }
    }

    private static native int[] createSubprocessNative(String[] cmd, String cwd, String[] env, int rows, int cols);
    private static native void setWindowSizeNative(int fd, int rows, int cols);
    private static native void sendSignalNative(int pid, int signal);
    private static native void destroyProcessNative(int pid);
    private static native int waitForNative(int pid);
    private static native void closeFdNative(int fd);
    private static native void reapZombiesNative();
}
