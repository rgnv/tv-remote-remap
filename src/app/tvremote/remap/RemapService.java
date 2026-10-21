package app.tvremote.remap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Global key filter + press state machine.
 *
 * Consumes the configured buttons, distinguishes short press, long press and
 * double press by elapsed time, and runs the action configured for each. Actions
 * are re-resolved from the live config when they fire, so editing or deleting a
 * mapping takes effect immediately.
 */
public class RemapService extends AccessibilityService {

    private static final String TAG = "TVRemap";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Press> presses = new HashMap<>();

    private static final class Press {
        Runnable longTask;
        Runnable singleTask;
        boolean longFired;
        boolean doubleArmed;
        long downAtMs;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        ConfigStore cfg = ConfigStore.get(this);
        Log.i(TAG, "service connected; buttons=" + cfg.rules().size()
                + " long=" + cfg.longPressMs() + "ms double=" + cfg.doublePressMs() + "ms");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Key filtering only; no event types are requested.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        final int code = event.getKeyCode();

        if (CaptureBus.isCapturing()) {
            // Navigation keys must keep working so the dialog stays cancellable.
            if (Keys.isNavigationKey(code)) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                CaptureBus.offer(code);
            }
            return true;
        }

        ConfigStore cfg = ConfigStore.get(this);
        KeyRule rule = cfg.ruleFor(code);
        if (rule == null) {
            return false; // not ours: let the system/app handle it
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                onDown(code, rule, cfg.longPressMs());
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            onUp(code, rule, cfg.longPressMs(), cfg.doublePressMs());
            return true;
        }
        return true;
    }

    private Press pressFor(int code) {
        Press p = presses.get(code);
        if (p == null) {
            p = new Press();
            presses.put(code, p);
        }
        return p;
    }

    private void onDown(final int code, KeyRule rule, int longPressMs) {
        final Press p = pressFor(code);

        if (p.singleTask != null) {         // second tap inside the double window
            handler.removeCallbacks(p.singleTask);
            p.singleTask = null;
            p.doubleArmed = true;
        } else {
            p.doubleArmed = false;
        }

        p.longFired = false;
        p.downAtMs = SystemClock.uptimeMillis();
        if (p.longTask != null) {
            handler.removeCallbacks(p.longTask);
            p.longTask = null;
        }

        if (rule.hasLong()) {
            p.longTask = new Runnable() {
                @Override
                public void run() {
                    p.longFired = true;
                    KeyRule live = ConfigStore.get(RemapService.this).ruleFor(code);
                    if (live == null) {
                        presses.remove(code);
                        return;
                    }
                    Log.i(TAG, live.name + " long press");
                    RemapService.this.run(live.longPress, live);
                }
            };
            handler.postDelayed(p.longTask, longPressMs);
        }
    }

    private void onUp(final int code, KeyRule rule, int longPressMs, int doublePressMs) {
        final Press p = presses.get(code);
        if (p == null) {
            return;
        }
        if (p.longTask != null) {
            handler.removeCallbacks(p.longTask);
            p.longTask = null;
        }

        long heldMs = SystemClock.uptimeMillis() - p.downAtMs;
        boolean tap = heldMs < longPressMs;

        if (p.longFired) {                  // long action already ran
            presses.remove(code);
            return;
        }
        if (p.doubleArmed && tap) {         // second tap completed
            presses.remove(code);
            Log.i(TAG, rule.name + " double press");
            run(rule.doublePress, rule);
            return;
        }
        p.doubleArmed = false;              // a hold after a first tap is not a double

        if (tap && rule.hasDouble()) {      // wait for a possible second tap
            p.singleTask = new Runnable() {
                @Override
                public void run() {
                    presses.remove(code);
                    KeyRule live = ConfigStore.get(RemapService.this).ruleFor(code);
                    if (live == null) {
                        return;
                    }
                    Log.i(TAG, live.name + " single press");
                    RemapService.this.run(live.single, live);
                }
            };
            handler.postDelayed(p.singleTask, doublePressMs);
            return;
        }

        presses.remove(code);
        Log.i(TAG, rule.name + (tap ? " single press" : " hold (no long action)"));
        run(rule.single, rule);
    }

    private void run(Action action, KeyRule rule) {
        if (action == null || !action.isSet()) {
            Log.i(TAG, rule.name + ": no action configured");
            return;
        }
        try {
            switch (action.type) {
                case Action.APP:
                    launchApp(action);
                    break;
                case Action.URL:
                case Action.INTENT: {
                    Intent i = action.buildIntent();
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    Log.i(TAG, "started " + action.summary(this));
                    break;
                }
                case Action.HTTP:
                    HttpTask.send(action.get("method"), action.get("url"),
                            action.get("headers"), action.get("body"));
                    Log.i(TAG, "webhook " + action.get("method") + " " + hostOf(action.get("url")));
                    break;
                case Action.HA: {
                    Action.HaRequest req = action.buildHaRequest();
                    HttpTask.send("POST", req.url, req.headers, req.body);
                    Log.i(TAG, "home assistant " + action.get("service"));
                    break;
                }
                case Action.VOICE:
                    voiceAssistant(action);
                    break;
                case Action.GLOBAL:
                    globalAction(action.get("global"));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, rule.name + ": action failed: " + e);
        }
    }

    /** scheme+host only: URLs often carry secrets in path or query. */
    private static String hostOf(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            return (u.getScheme() == null ? "" : u.getScheme() + "://") + u.getHost();
        } catch (Exception e) {
            return "(unparseable url)";
        }
    }

    private void launchApp(Action action) {
        String pkg = action.get("pkg");
        String activity = action.get("activity");
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            if (!activity.isEmpty()) {
                intent.setClassName(pkg, activity);
            } else {
                intent.setPackage(pkg);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.i(TAG, "launched " + pkg + (activity.isEmpty() ? "" : "/" + activity));
            return;
        } catch (Exception e) {
            Log.w(TAG, "explicit launch failed: " + e);
        }
        try {
            PackageManager pm = getPackageManager();
            Intent leanback = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (leanback == null) {
                leanback = pm.getLaunchIntentForPackage(pkg);
            }
            if (leanback != null) {
                leanback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(leanback);
                Log.i(TAG, "launched " + pkg);
            } else {
                Log.e(TAG, "no launch intent for " + pkg);
            }
        } catch (Exception e) {
            Log.e(TAG, "launch failed for " + pkg + ": " + e);
        }
    }

    private void voiceAssistant(Action action) {
        String pkg = action.get("pkg");
        if (!pkg.isEmpty()) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                Log.i(TAG, "voice assistant app " + pkg);
                return;
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_VOICE_COMMAND);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Log.i(TAG, "voice assistant (ACTION_VOICE_COMMAND)");
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        try {
            Intent i = new Intent(Intent.ACTION_ASSIST);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Log.i(TAG, "voice assistant (ACTION_ASSIST)");
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "no voice assistant available");
        }
    }

    private void globalAction(String name) {
        int action;
        switch (name == null ? "" : name) {
            case "HOME":
                action = GLOBAL_ACTION_HOME;
                break;
            case "BACK":
                action = GLOBAL_ACTION_BACK;
                break;
            case "RECENTS":
                action = GLOBAL_ACTION_RECENTS;
                break;
            case "NOTIFICATIONS":
                action = GLOBAL_ACTION_NOTIFICATIONS;
                break;
            case "QUICK_SETTINGS":
                action = GLOBAL_ACTION_QUICK_SETTINGS;
                break;
            case "POWER_DIALOG":
                action = GLOBAL_ACTION_POWER_DIALOG;
                break;
            case "LOCK_SCREEN":
                if (Build.VERSION.SDK_INT < 28) {
                    Log.w(TAG, "lock screen needs Android 9+");
                    return;
                }
                action = GLOBAL_ACTION_LOCK_SCREEN;
                break;
            case "SCREENSHOT":
                if (Build.VERSION.SDK_INT < 28) {
                    Log.w(TAG, "screenshot needs Android 9+");
                    return;
                }
                action = GLOBAL_ACTION_TAKE_SCREENSHOT;
                break;
            default:
                Log.w(TAG, "unknown global action " + name);
                return;
        }
        performGlobalAction(action);
        Log.i(TAG, "global action " + name);
    }
}
