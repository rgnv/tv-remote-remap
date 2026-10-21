package app.tvremote.remap;

/** Configuration for one physical button: what its press types do. */
public final class KeyRule {

    public final int code;
    public String name;
    public Action single;
    public Action longPress;
    public Action doublePress;

    public KeyRule(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public boolean hasLong() {
        return longPress != null && longPress.isSet();
    }

    public boolean hasDouble() {
        return doublePress != null && doublePress.isSet();
    }

    public boolean hasAnyAction() {
        return (single != null && single.isSet()) || hasLong() || hasDouble();
    }

    /** Compact summary for the main list: which press types do what. */
    public String summary(android.content.Context c) {
        StringBuilder sb = new StringBuilder();
        if (single != null && single.isSet()) {
            sb.append("Single · ").append(single.summary(c));
        }
        if (hasLong()) {
            sb.append(sb.length() > 0 ? "   " : "").append("Long · ").append(longPress.summary(c));
        }
        if (hasDouble()) {
            sb.append(sb.length() > 0 ? "   " : "").append("Double · ").append(doublePress.summary(c));
        }
        return sb.length() == 0 ? "No actions — button is disabled" : sb.toString();
    }
}
