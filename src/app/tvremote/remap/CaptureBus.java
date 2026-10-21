package app.tvremote.remap;

import android.os.SystemClock;

/** One-shot "press a button to add it" channel between the service and the UI. */
final class CaptureBus {

    interface Listener {
        void onKey(int code);
    }

    /** Capture never blocks the remote for longer than this. */
    private static final long TIMEOUT_MS = 30_000;

    private static Listener listener;
    private static long deadlineMs;

    private CaptureBus() {
    }

    static synchronized void start(Listener l) {
        listener = l;
        deadlineMs = SystemClock.uptimeMillis() + TIMEOUT_MS;
    }

    static synchronized void stop() {
        listener = null;
    }

    static synchronized boolean isCapturing() {
        if (listener != null && SystemClock.uptimeMillis() > deadlineMs) {
            listener = null; // timed out: never leave the remote swallowed
        }
        return listener != null;
    }

    static synchronized void offer(int code) {
        if (listener != null) {
            Listener l = listener;
            listener = null;
            l.onKey(code);
        }
    }
}
