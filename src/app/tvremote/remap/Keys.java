package app.tvremote.remap;

import android.view.KeyEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Key guards and range clamps shared by capture, import and the service. */
final class Keys {

    /** Keys the user needs to operate the TV; remapping them traps the remote. */
    private static final Set<Integer> NAVIGATION = new HashSet<>(Arrays.asList(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER));

    private Keys() {
    }

    static boolean isNavigationKey(int code) {
        return NAVIGATION.contains(code);
    }

    static boolean isValidCode(int code) {
        return code > 0 && code <= KeyEvent.getMaxKeyCode();
    }

    static int clampMs(int value) {
        return Math.max(100, Math.min(5000, value));
    }
}
