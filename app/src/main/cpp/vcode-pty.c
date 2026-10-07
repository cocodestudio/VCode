#define _GNU_SOURCE
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <pty.h>
#include <dirent.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/syscall.h>
#include <signal.h>
#include <errno.h>
#include <android/log.h>

#define TAG "VCodePty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static int pt_open(int *amaster, int *aslave, char *name, struct winsize *winp) {
    if (!amaster || !aslave) return -1;

    // 1. Try standard openpty first
    if (openpty(amaster, aslave, name, NULL, winp) == 0) {
        if (*amaster >= 0) {
            fcntl(*amaster, F_SETFD, FD_CLOEXEC);
        }
        if (*aslave >= 0) {
            fcntl(*aslave, F_SETFD, FD_CLOEXEC);
        }
        return 0;
    }

    // 2. Fallback to opening /dev/ptmx manually
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC | O_NOCTTY);
    if (ptm < 0) {
        ptm = open("/dev/pts/ptmx", O_RDWR | O_CLOEXEC | O_NOCTTY);
    }
    if (ptm < 0) {
        LOGE("Failed to open /dev/ptmx: %s", strerror(errno));
        return -1;
    }

    if (grantpt(ptm) != 0 || unlockpt(ptm) != 0) {
        LOGE("grantpt or unlockpt failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }

    char devname[64];
    if (ptsname_r(ptm, devname, sizeof(devname)) != 0) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }

    if (name) {
        strncpy(name, devname, 63);
        name[63] = '\0';
    }

    int pts = open(devname, O_RDWR | O_CLOEXEC | O_NOCTTY);
    if (pts < 0) {
        LOGE("Failed to open slave %s: %s", devname, strerror(errno));
        close(ptm);
        return -1;
    }

    if (winp) {
        ioctl(ptm, TIOCSWINSZ, winp);
    }

    fcntl(ptm, F_SETFD, FD_CLOEXEC);
    fcntl(pts, F_SETFD, FD_CLOEXEC);
    *amaster = ptm;
    *aslave = pts;
    return 0;
}

static void close_all_inherited_fds(void) {
#if defined(__NR_close_range)
    if (syscall(__NR_close_range, STDERR_FILENO + 1, ~0U, 0) == 0) {
        return;
    }
#endif

    // Fallback A: Iterate /proc/self/fd for fast, exact descriptor closing
    DIR *dir = opendir("/proc/self/fd");
    if (dir) {
        int dir_fd = dirfd(dir);
        struct dirent *de;
        while ((de = readdir(dir)) != NULL) {
            if (de->d_name[0] == '.') continue;
            char *endptr = NULL;
            long fd = strtol(de->d_name, &endptr, 10);
            if (endptr && *endptr == '\0') {
                if (fd > STDERR_FILENO && fd != dir_fd) {
                    close((int) fd);
                }
            }
        }
        closedir(dir);
        return;
    }

    // Fallback B: Loop through system open file limits
    int max_fd = (int) sysconf(_SC_OPEN_MAX);
    if (max_fd < 32) max_fd = 1024;
    if (max_fd > 2048) max_fd = 2048;
    for (int fd = STDERR_FILENO + 1; fd < max_fd; fd++) {
        close(fd);
    }
}

static void free_string_array(char **arr, jsize len) {
    if (!arr) return;
    for (jsize i = 0; i < len; i++) {
        if (arr[i]) {
            free(arr[i]);
            arr[i] = NULL;
        }
    }
    free(arr);
}

#include <pthread.h>

#define MAX_TRACKED_PIDS 64
static pid_t g_tracked_pids[MAX_TRACKED_PIDS];
static pthread_mutex_t g_pids_mutex = PTHREAD_MUTEX_INITIALIZER;

static void track_child_pid(pid_t pid) {
    if (pid <= 1) return;
    pthread_mutex_lock(&g_pids_mutex);
    for (int i = 0; i < MAX_TRACKED_PIDS; i++) {
        if (g_tracked_pids[i] == 0) {
            g_tracked_pids[i] = pid;
            break;
        }
    }
    pthread_mutex_unlock(&g_pids_mutex);
}

static void untrack_child_pid(pid_t pid) {
    if (pid <= 1) return;
    pthread_mutex_lock(&g_pids_mutex);
    for (int i = 0; i < MAX_TRACKED_PIDS; i++) {
        if (g_tracked_pids[i] == pid) {
            g_tracked_pids[i] = 0;
            break;
        }
    }
    pthread_mutex_unlock(&g_pids_mutex);
}

static void reap_tracked_zombies(void) {
    pthread_mutex_lock(&g_pids_mutex);
    for (int i = 0; i < MAX_TRACKED_PIDS; i++) {
        pid_t p = g_tracked_pids[i];
        if (p > 1) {
            int status = 0;
            pid_t res = waitpid(p, &status, WNOHANG);
            if (res > 0 || (res < 0 && errno == ECHILD)) {
                g_tracked_pids[i] = 0;
            }
        }
    }
    pthread_mutex_unlock(&g_pids_mutex);
}

JNIEXPORT jintArray JNICALL
Java_com_cocode_vcode_ide_core_terminal_pty_PtyBridge_createSubprocessNative(
    JNIEnv *env, jclass clazz,
    jobjectArray cmd, jstring cwd, jobjectArray envArr,
    jint rows, jint cols) {
    (void) clazz;

    if (!cmd) {
        LOGE("createSubprocessNative: cmd is null");
        return NULL;
    }

    jsize cmdLen = (*env)->GetArrayLength(env, cmd);
    if (cmdLen <= 0) {
        LOGE("createSubprocessNative: cmd is empty");
        return NULL;
    }

    char **argv = (char **) malloc(sizeof(char *) * (cmdLen + 1));
    if (!argv) return NULL;

    for (jsize i = 0; i < cmdLen; i++) {
        jstring str = (jstring) (*env)->GetObjectArrayElement(env, cmd, i);
        if (str) {
            const char *utf = (*env)->GetStringUTFChars(env, str, NULL);
            argv[i] = utf ? strdup(utf) : NULL;
            if (utf) (*env)->ReleaseStringUTFChars(env, str, utf);
            (*env)->DeleteLocalRef(env, str);
        } else {
            argv[i] = NULL;
        }
    }
    argv[cmdLen] = NULL;

    if (!argv[0]) {
        LOGE("createSubprocessNative: argv[0] is null");
        free_string_array(argv, cmdLen);
        return NULL;
    }

    char *cwdStr = NULL;
    if (cwd) {
        const char *utf = (*env)->GetStringUTFChars(env, cwd, NULL);
        if (utf) {
            cwdStr = strdup(utf);
            (*env)->ReleaseStringUTFChars(env, cwd, utf);
        }
    }

    char **envp = NULL;
    jsize envLen = 0;
    if (envArr) {
        envLen = (*env)->GetArrayLength(env, envArr);
        envp = (char **) malloc(sizeof(char *) * (envLen + 1));
        if (envp) {
            for (jsize i = 0; i < envLen; i++) {
                jstring str = (jstring) (*env)->GetObjectArrayElement(env, envArr, i);
                if (str) {
                    const char *utf = (*env)->GetStringUTFChars(env, str, NULL);
                    envp[i] = utf ? strdup(utf) : NULL;
                    if (utf) (*env)->ReleaseStringUTFChars(env, str, utf);
                    (*env)->DeleteLocalRef(env, str);
                } else {
                    envp[i] = NULL;
                }
            }
            envp[envLen] = NULL;
        } else {
            free_string_array(argv, cmdLen);
            if (cwdStr) free(cwdStr);
            return NULL;
        }
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);

    int master = -1;
    int slave = -1;
    char pts_name[64];
    memset(pts_name, 0, sizeof(pts_name));

    if (pt_open(&master, &slave, pts_name, &ws) != 0) {
        LOGE("Failed to allocate PTY");
        free_string_array(argv, cmdLen);
        if (cwdStr) free(cwdStr);
        if (envp) free_string_array(envp, envLen);
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        close(master);
        close(slave);
        free_string_array(argv, cmdLen);
        if (cwdStr) free(cwdStr);
        if (envp) free_string_array(envp, envLen);
        return NULL;
    }

    if (pid == 0) {
        // Child process
        // 1. Establish new session and become process group leader
        setsid();

        // 2. Clear FD_CLOEXEC on slave
        fcntl(slave, F_SETFD, 0);

        // 3. Set controlling terminal
        ioctl(slave, TIOCSCTTY, 0);
        ioctl(slave, TIOCSWINSZ, &ws);

        // 4. Connect standard I/O to PTY slave
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);

        if (slave > STDERR_FILENO) close(slave);
        if (master > STDERR_FILENO) close(master);

        // 5. Close all inherited open file descriptors >= 3
        close_all_inherited_fds();

        // 6. Reset signal mask and signal handlers inherited from ART JVM
        sigset_t sigs;
        sigemptyset(&sigs);
        sigprocmask(SIG_SETMASK, &sigs, NULL);

        signal(SIGINT, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGTSTP, SIG_DFL);
        signal(SIGPIPE, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGHUP, SIG_DFL);
        signal(SIGTERM, SIG_DFL);
        signal(SIGTTIN, SIG_DFL);
        signal(SIGTTOU, SIG_DFL);

        // 7. Set working directory
        if (cwdStr && strlen(cwdStr) > 0) {
            if (chdir(cwdStr) != 0) {
                // Ignore error if chdir fails, process will start in inherited dir
            }
        }

        // 8. Clear host LD_PRELOAD injection
        unsetenv("LD_PRELOAD");

        // 9. Apply environment variables
        if (envp) {
            for (jsize i = 0; i < envLen; i++) {
                if (envp[i]) {
                    putenv(envp[i]);
                }
            }
        }

        // 10. Execute target program
        execvp(argv[0], argv);

        // If execvp fails:
        const char *err = strerror(errno);
        fprintf(stderr, "\r\n[VCode Terminal] Failed to execute %s: %s\r\n", argv[0], err);
        _exit(127);
    }

    // Parent process: immediately close slave PTY and release temporary strings
    close(slave);
    free_string_array(argv, cmdLen);
    if (cwdStr) free(cwdStr);
    if (envp) free_string_array(envp, envLen);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (!result) {
        close(master);
        kill(-pid, SIGKILL);
        kill(pid, SIGKILL);
        waitpid(pid, NULL, 0);
        return NULL;
    }

    track_child_pid((pid_t) pid);

    jint data[2] = { (jint) pid, (jint) master };
    (*env)->SetIntArrayRegion(env, result, 0, 2, data);
    return result;
}

JNIEXPORT void JNICALL
Java_com_cocode_vcode_ide_core_terminal_pty_PtyBridge_setWindowSizeNative(
    JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    (void) env;
    (void) clazz;
    if (fd >= 0 && rows > 0 && cols > 0) {
        struct winsize ws;
        memset(&ws, 0, sizeof(ws));
        ws.ws_row = (unsigned short) rows;
        ws.ws_col = (unsigned short) cols;
        ioctl(fd, TIOCSWINSZ, &ws);
    }
}

JNIEXPORT void JNICALL
Java_com_cocode_vcode_ide_core_terminal_pty_PtyBridge_sendSignalNative(
    JNIEnv *env, jclass clazz, jint pid, jint sig) {
    (void) env;
    (void) clazz;
    if (pid > 1) {
        kill(-pid, sig);
        kill(pid, sig);

        if (sig == SIGKILL || sig == SIGTERM) {
            int status = 0;
            pid_t res = waitpid((pid_t) pid, &status, WNOHANG);
            if (res > 0 || (res < 0 && errno == ECHILD)) {
                untrack_child_pid((pid_t) pid);
            }
        }
    }
}

JNIEXPORT void JNICALL
Java_com_cocode_vcode_ide_core_terminal_pty_PtyBridge_destroyProcessNative(
    JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid <= 1) return;

    // 1. Send SIGTERM to process group and process
    kill(-pid, SIGTERM);
    kill(pid, SIGTERM);

    // 2. Poll up to 10ms (5 iterations * 2ms) to give it a chance to terminate gracefully
    int reaped = 0;
    int status = 0;
    for (int i = 0; i < 5; i++) {
        pid_t res = waitpid((pid_t) pid, &status, WNOHANG);
        if (res == (pid_t) pid || (res < 0 && errno == ECHILD)) {
            reaped = 1;
            break;
        }
        usleep(2000); // 2ms
    }

    // 3. If process hasn't exited, escalate to SIGKILL to guarantee termination
    if (!reaped) {
        kill(-pid, SIGKILL);
        kill(pid, SIGKILL);
        waitpid((pid_t) pid, &status, WNOHANG);
    }

    // Untrack specifically without touching global process table
    untrack_child_pid((pid_t) pid);
}

JNIEXPORT jint JNICALL
Java_com_cocode_vcode_ide_core_terminal_pty_PtyBridge_waitForNative(
    JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid <= 1) return -1;

    int status = 0;
    while (waitpid((pid_t) pid, &status, 0) < 0) {
        if (errno == ECHILD) {
            // Already reaped or not a valid child process
            untrack_child_pid((pid_t) pid);
            return -1;
        }
        if (errno != EINTR) {
            untrack_child_pid((pid_t) pid);
            return -1;
        }
    }

    untrack_child_pid((pid_t) pid);

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return status;
}

JNIEXPORT void JNICALL
Java_com_cocode_vcode_ide_core_terminal_pty_PtyBridge_closeFdNative(
    JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    if (fd >= 0) {
        close(fd);
    }
}

JNIEXPORT void JNICALL
Java_com_cocode_vcode_ide_core_terminal_pty_PtyBridge_reapZombiesNative(
    JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    reap_tracked_zombies();
}