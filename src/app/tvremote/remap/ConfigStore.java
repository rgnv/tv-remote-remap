package app.tvremote.remap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent configuration, stored as a single JSON blob in SharedPreferences.
 *
 * Parsing is atomic and validating: a malformed blob leaves the previous
 * configuration untouched instead of wiping it, and impossible values (bad key
 * codes, duplicate buttons, navigation keys, unknown action types, out-of-range
 * timings) are rejected.
 */
public final class ConfigStore {

    private static final String PREFS = "config";
    private static final String KEY = "json";
    private static final String TAG = "TVRemap";

    private static ConfigStore instance;

    private final Context appContext;
    private String raw = "";
    private final Map<Integer, KeyRule> rules = new LinkedHashMap<>();
    private int longPressMs = 500;
    private int doublePressMs = 350;

    private ConfigStore(Context context) {
        appContext = context.getApplicationContext();
        parse(prefs().getString(KEY, ""));
    }

    public static synchronized ConfigStore get(Context context) {
        if (instance == null) {
            instance = new ConfigStore(context);
        } else {
            instance.reloadIfChanged();
        }
        return instance;
    }

    public synchronized void reloadIfChanged() {
        String now = prefs().getString(KEY, "");
        if (now != null && !now.equals(raw)) {
            parse(now);
            raw = now; // do not retry a corrupt blob on every key press
        }
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void reload() {
        String now = prefs().getString(KEY, "");
        parse(now);
        raw = now == null ? "" : now;
    }

    /** Parses into temporaries and only commits when everything is valid. */
    private boolean parse(String json) {
        Map<Integer, KeyRule> parsed = new LinkedHashMap<>();
        int newLong = 500;
        int newDouble = 350;
        try {
            if (json != null && !json.isEmpty()) {
                JSONObject root = new JSONObject(json);
                newLong = Keys.clampMs(root.optInt("longPressMs", 500));
                newDouble = Keys.clampMs(root.optInt("doublePressMs", 350));
                JSONArray arr = root.optJSONArray("buttons");
                if (arr == null) {
                    throw new IllegalArgumentException("missing \"buttons\" array");
                }
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    int code = o.getInt("code");
                    if (!Keys.isValidCode(code)) {
                        throw new IllegalArgumentException("bad key code " + code);
                    }
                    if (Keys.isNavigationKey(code)) {
                        throw new IllegalArgumentException("refusing navigation key " + code);
                    }
                    if (parsed.containsKey(code)) {
                        throw new IllegalArgumentException("duplicate key " + code);
                    }
                    KeyRule r = new KeyRule(code, o.optString("name", "Button"));
                    r.single = Action.fromJson(o.optJSONObject("single"));
                    r.longPress = Action.fromJson(o.optJSONObject("long"));
                    r.doublePress = Action.fromJson(o.optJSONObject("double"));
                    parsed.put(code, r);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "config rejected, keeping previous: " + e);
            return false;
        }
        rules.clear();
        rules.putAll(parsed);
        longPressMs = newLong;
        doublePressMs = newDouble;
        raw = json == null ? "" : json;
        return true;
    }

    public synchronized String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("longPressMs", longPressMs);
            root.put("doublePressMs", doublePressMs);
            JSONArray arr = new JSONArray();
            for (KeyRule r : rules.values()) {
                JSONObject o = new JSONObject();
                o.put("code", r.code);
                o.put("name", r.name);
                if (r.single != null) {
                    o.put("single", r.single.toJson());
                }
                if (r.longPress != null) {
                    o.put("long", r.longPress.toJson());
                }
                if (r.doublePress != null) {
                    o.put("double", r.doublePress.toJson());
                }
                arr.put(o);
            }
            root.put("buttons", arr);
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public synchronized void save() {
        String json = toJson();
        if (!parse(json)) {
            Log.e(TAG, "refusing to persist a config that does not parse");
            return;
        }
        prefs().edit().putString(KEY, json).apply();
    }

    /** Replaces everything with an imported config; throws if it is invalid. */
    public synchronized void importJson(String json) throws Exception {
        if (!parse(json)) {
            throw new IllegalArgumentException("invalid configuration (previous one kept)");
        }
        prefs().edit().putString(KEY, json).apply();
    }

    public synchronized List<KeyRule> rules() {
        return new ArrayList<>(rules.values());
    }

    public synchronized KeyRule ruleFor(int code) {
        return rules.get(code);
    }

    public synchronized void put(KeyRule rule) {
        if (!Keys.isValidCode(rule.code) || Keys.isNavigationKey(rule.code)) {
            throw new IllegalArgumentException("key " + rule.code + " cannot be remapped");
        }
        rules.put(rule.code, rule);
        save();
    }

    public synchronized void remove(int code) {
        rules.remove(code);
        save();
    }

    public synchronized void clear() {
        rules.clear();
        raw = "";
        prefs().edit().remove(KEY).apply();
    }

    public synchronized int longPressMs() {
        return longPressMs;
    }

    public synchronized int doublePressMs() {
        return doublePressMs;
    }

    public synchronized void setTimings(int longMs, int doubleMs) {
        longPressMs = Keys.clampMs(longMs);
        doublePressMs = Keys.clampMs(doubleMs);
        save();
    }
}
