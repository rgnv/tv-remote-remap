package app.tvremote.remap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared TV look: dark theme, rounded focus cards, D-pad friendly layout. */
final class Ui {

    static final int BG = 0xFF0A0C10;
    static final int SURFACE = 0xFF141922;
    static final int SURFACE_FOCUS = 0xFF1D2633;
    static final int ACCENT = 0xFF5B8CFF;
    static final int TEXT = 0xFFF2F5F9;
    static final int MUTED = 0xFF9AA5B8;
    static final int DIM = 0xFF6E7A8F;
    static final int GREEN = 0xFF3DDC97;
    static final int RED = 0xFFFF6B6B;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(int fill, int stroke, int strokeWidthPx, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            d.setStroke(strokeWidthPx, stroke);
        }
        return d;
    }

    private static android.graphics.Typeface MEDIUM;

    private static android.graphics.Typeface medium() {
        if (MEDIUM == null) {
            MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
        return MEDIUM;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(medium());
        }
        return t;
    }

    static View scaffold(Activity a, String title, String subtitle, View body) {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(BG);

        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(a, 48), dp(a, 30), dp(a, 48), dp(a, 6));
        TextView t = text(a, title, 30, TEXT, true);
        header.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(a, subtitle, 15, MUTED, false);
            s.setPadding(0, dp(a, 6), 0, 0);
            header.addView(s);
        }
        column.addView(header);

        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setFocusable(false);
        content.setPadding(dp(a, 48), dp(a, 10), dp(a, 48), dp(a, 36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (body != null) {
            content.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView hints = text(a, "▲ ▼  navigate      ●  select      ←  back", 12, DIM, false);
        hints.setGravity(Gravity.END);
        hints.setPadding(dp(a, 48), dp(a, 8), dp(a, 48), dp(a, 16));
        column.addView(hints);

        return root;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    static TextView section(Context c, String label) {
        TextView t = text(c, label.toUpperCase(), 11, DIM, true);
        t.setLetterSpacing(0.14f);
        t.setPadding(dp(c, 8), dp(c, 22), 0, dp(c, 8));
        return t;
    }

    static View spacer(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return v;
    }

    /** A focusable card row: badge, title, subtitle, chevron. */
    static final class Row extends LinearLayout {
        final TextView badge;
        final TextView title;
        final TextView subtitle;
        final TextView chevron;

        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            int p = dp(c, 18);
            setPadding(p, dp(c, 14), p, dp(c, 14));
            setMinimumHeight(dp(c, 88));

            // Allocated once per row and swapped on focus, not rebuilt per event.
            final GradientDrawable bgNormal = round(SURFACE, 0, 0, dp(c, 18));
            final GradientDrawable bgFocused = round(SURFACE_FOCUS, ACCENT, dp(c, 2), dp(c, 18));
            final GradientDrawable badgeNormal = round(0xFF1B2431, 0, 0, dp(c, 14));
            final GradientDrawable badgeFocused = round(ACCENT, 0, 0, dp(c, 14));
            setBackground(bgNormal);

            badge = Ui.text(c, "", 18, ACCENT, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(badgeNormal);
            LayoutParams bp = new LayoutParams(dp(c, 46), dp(c, 46));
            bp.rightMargin = dp(c, 16);
            addView(badge, bp);

            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(VERTICAL);
            title = Ui.text(c, "", 20, TEXT, true);
            subtitle = Ui.text(c, "", 15, MUTED, false);
            subtitle.setPadding(0, dp(c, 3), 0, 0);
            title.setSingleLine(true);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(title);
            texts.addView(subtitle);
            addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            chevron = Ui.text(c, "›", 26, DIM, false);
            chevron.setPadding(dp(c, 12), 0, 0, 0);
            addView(chevron);

            setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    float s = hasFocus ? 1.025f : 1f;
                    v.animate().scaleX(s).scaleY(s).setDuration(110).start();
                    v.setBackground(hasFocus ? bgFocused : bgNormal);
                    badge.setBackground(hasFocus ? badgeFocused : badgeNormal);
                    badge.setTextColor(hasFocus ? BG : ACCENT);
                    v.setElevation(hasFocus ? dp(getContext(), 10) : 0);
                }
            });
        }

        Row badge(String s) {
            badge.setText(s);
            return this;
        }

        Row text(String t, String sub) {
            setContentDescription(t);
            title.setText(t);
            subtitle.setText(sub == null ? "" : sub);
            subtitle.setVisibility(sub == null || sub.isEmpty() ? GONE : VISIBLE);
            return this;
        }
    }

    static Row row(Context c, String badge, String title, String sub, View.OnClickListener click) {
        Row r = new Row(c);
        r.badge(badge).text(title, sub);
        r.setOnClickListener(click);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    static EditText field(Context c, String hint, String value) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setBackground(round(0xFF10151D, 0xFF2A3444, dp(c, 1), dp(c, 12)));
        e.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        e.setSingleLine(true);
        return e;
    }

    static EditText multiline(Context c, String hint, String value) {
        EditText e = field(c, hint, value);
        e.setSingleLine(false);
        e.setMinLines(2);
        e.setGravity(Gravity.TOP | Gravity.START);
        return e;
    }

    static LinearLayout fieldBlock(Context c, String label, View field) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(c, label, 12, MUTED, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 6), dp(c, 12), 0, dp(c, 6));
        l.addView(t);
        l.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }
}
