package app.tvremote.remap;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** One thing a button (or press type) can do. */
public final class Action {

    public static final String NONE = "NONE";
    public static final String APP = "APP";
    public static final String URL = "URL";
    public static final String HTTP = "HTTP";
    public static final String HA = "HA";
    public static final String VOICE = "VOICE";
    public static final String GLOBAL = "GLOBAL";
    public static final String INTENT = "INTENT";

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            NONE, APP, URL, HTTP, HA, VOICE, GLOBAL, INTENT));

    public final String type;
    private final Map<String, String> params = new LinkedHashMap<>();

    public Action(String type) {
        this.type = type;
    }

    public static Action none() {
        return new Action(NONE);
    }

    public String get(String key) {
        String v = params.get(key);
        return v == null ? "" : v;
    }

    public Action set(String key, String value) {
        params.put(key, value == null ? "" : value);
        return this;
    }

    public boolean isNone() {
        return NONE.equals(type);
    }

    public boolean isSet() {
        return type != null && !NONE.equals(type);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type);
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    o.put(e.getKey(), e.getValue());
                }
            }
        } catch (JSONException ignored) {
        }
        return o;
    }

    /** Rejects unknown action types instead of storing an action that can never run. */
    public static Action fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        String type = o.optString("type", NONE);
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("unknown action type: " + type);
        }
        Action a = new Action(type);
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!"type".equals(k)) {
                a.set(k, o.optString(k, ""));
            }
        }
        return a;
    }

    /** Short label for list rows. */
    public String summary(Context context) {
        switch (type) {
            case APP: {
                String label = get("label");
                if (label.isEmpty()) {
                    label = get("pkg");
                }
                return "Launch " + label;
            }
            case URL:
                return "Open " + shorten(get("url"));
            case HTTP: {
                String m = get("method");
                return (m.isEmpty() ? "GET" : m) + " " + shorten(get("url"));
            }
            case HA: {
                String svc = get("service");
                String ent = get("entity");
                return "Home Assistant " + svc + (ent.isEmpty() ? "" : " · " + ent);
            }
            case VOICE:
                return get("pkg").isEmpty() ? "Voice assistant" : "Voice assistant (" + get("pkg") + ")";
            case GLOBAL:
                return "System: " + get("global");
            case INTENT:
                return "Intent " + shorten(get("uri"));
            default:
                return "Disabled";
        }
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        s = s.replaceFirst("^https?://", "");
        return s.length() > 44 ? s.substring(0, 41) + "…" : s;
    }

    /**
     * Builds the intent for APP / URL / INTENT actions.
     * URL is restricted to http(s); INTENT is forced to an implicit intent so an
     * imported config cannot launch a specific private component.
     */
    public Intent buildIntent() throws Exception {
        switch (type) {
            case APP: {
                String pkg = get("pkg");
                String activity = get("activity");
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                if (!activity.isEmpty()) {
                    i.setClassName(pkg, activity);
                } else {
                    i.setPackage(pkg);
                }
                return i;
            }
            case URL: {
                Uri uri = Uri.parse(get("url"));
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    throw new IllegalArgumentException("URL scheme not allowed: " + scheme);
                }
                return new Intent(Intent.ACTION_VIEW, uri);
            }
            case INTENT: {
                Intent i = Intent.parseUri(get("uri"), Intent.URI_INTENT_SCHEME);
                i.setComponent(null);
                i.setPackage(null);
                i.removeCategory(Intent.CATEGORY_BROWSABLE);
                return i;
            }
            default:
                return null;
        }
    }

    /** Shared Home Assistant request builder (service path and Test button). */
    static final class HaRequest {
        final String url;
        final String headers;
        final String body;

        HaRequest(String url, String headers, String body) {
            this.url = url;
            this.headers = headers;
            this.body = body;
        }
    }

    /** Returns null when the service name or extra JSON is invalid. */
    HaRequest buildHaRequest() throws Exception {
        String base = get("base").replaceAll("/+$", "");
        String service = get("service");
        if (!service.matches("[a-z0-9_]+\\.[a-z0-9_]+")) {
            throw new IllegalArgumentException("bad service name: " + service);
        }
        JSONObject body = new JSONObject();
        String extra = get("data");
        if (!extra.trim().isEmpty()) {
            body = new JSONObject(extra); // invalid JSON throws: never fire a partial call
        }
        String entity = get("entity");
        if (!entity.isEmpty() && !body.has("entity_id")) {
            body.put("entity_id", entity);
        }
        String headers = "Content-Type: application/json";
        String token = get("token");
        if (!token.isEmpty()) {
            headers = "Authorization: Bearer " + token + "\n" + headers;
        }
        return new HaRequest(base + "/api/services/" + service, headers, body.toString());
    }
}
