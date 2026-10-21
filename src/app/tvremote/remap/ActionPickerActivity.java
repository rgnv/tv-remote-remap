package app.tvremote.remap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/** Chooses what a press type does. */
public class ActionPickerActivity extends Activity {

    static final int REQ_APP = 1;
    static final int REQ_CONFIG = 2;
    static final int REQ_GLOBAL = 3;

    private static final String[] LABELS = {
            "Disabled", "Launch app", "Open URL", "Webhook (HTTP)",
            "Home Assistant", "Voice assistant", "System action", "Intent URI"};
    private static final String[] TYPES = {
            Action.NONE, Action.APP, Action.URL, Action.HTTP,
            Action.HA, Action.VOICE, Action.GLOBAL, Action.INTENT};
    private static final String[] HINTS = {
            "Swallow the key, do nothing",
            "Start an installed app",
            "Open a link in a browser",
            "GET / POST a URL — works with HA webhooks, n8n, anything",
            "Call a Home Assistant service with a long-lived token",
            "Open the system assistant, or a specific assistant app",
            "Back, Home, Recents, notifications, power menu…",
            "Fire an explicit intent URI"};

    private int code;
    private String slot;
    private ConfigStore cfg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        code = getIntent().getIntExtra(MainActivity.EXTRA_CODE, -1);
        slot = getIntent().getStringExtra(ButtonDetailActivity.EXTRA_SLOT);
        cfg = ConfigStore.get(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeyRule rule = cfg.ruleFor(code);
        if (rule == null) {
            finish();
            return;
        }
        Action current = current(rule);

        LinearLayout body = Ui.column(this);
        for (int i = 0; i < LABELS.length; i++) {
            final String type = TYPES[i];
            String sub = HINTS[i];
            if (current != null && type.equals(current.type)) {
                sub = "Current · " + sub;
            }
            Ui.Row row = Ui.row(this, String.valueOf(i + 1), LABELS[i], sub,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            choose(type);
                        }
                    });
            if (current != null && type.equals(current.type)) {
                row.badge.setTextColor(Ui.GREEN);
            }
            body.addView(row);
            body.addView(Ui.spacer(this, 8));
        }

        String title = "single".equals(slot) ? "Single press"
                : "long".equals(slot) ? "Long press" : "Double press";
        setContentView(Ui.scaffold(this, title, "Choose an action", body));
        body.getChildAt(0).requestFocus();
    }

    private Action current(KeyRule rule) {
        if ("single".equals(slot)) {
            return rule.single;
        }
        if ("long".equals(slot)) {
            return rule.longPress;
        }
        return rule.doublePress;
    }

    private void choose(String type) {
        if (Action.NONE.equals(type)) {
            save(Action.none());
            finish();
            return;
        }
        if (Action.APP.equals(type)) {
            Intent i = new Intent(this, AppPickerActivity.class);
            startActivityForResult(i, REQ_APP);
            return;
        }
        if (Action.GLOBAL.equals(type)) {
            Intent i = new Intent(this, ListPickerActivity.class);
            i.putExtra(ListPickerActivity.EXTRA_TITLE, "System action");
            i.putExtra(ListPickerActivity.EXTRA_ITEMS, GlobalActions.NAMES);
            startActivityForResult(i, REQ_GLOBAL);
            return;
        }
        Intent i = new Intent(this, ActionConfigActivity.class);
        i.putExtra(MainActivity.EXTRA_CODE, code);
        i.putExtra(ButtonDetailActivity.EXTRA_SLOT, slot);
        i.putExtra(ActionConfigActivity.EXTRA_TYPE, type);
        startActivityForResult(i, REQ_CONFIG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQ_APP) {
            Action a = new Action(Action.APP)
                    .set("pkg", data.getStringExtra(AppPickerActivity.EXTRA_PKG))
                    .set("activity", data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY))
                    .set("label", data.getStringExtra(AppPickerActivity.EXTRA_LABEL));
            save(a);
            finish();
        } else if (requestCode == REQ_GLOBAL) {
            int idx = data.getIntExtra(ListPickerActivity.EXTRA_INDEX, -1);
            if (idx >= 0) {
                save(new Action(Action.GLOBAL).set("global", GlobalActions.VALUES[idx]));
                finish();
            }
        } else if (requestCode == REQ_CONFIG) {
            finish();
        }
    }

    private void save(Action a) {
        KeyRule rule = cfg.ruleFor(code);
        if (rule == null) {
            return;
        }
        if ("single".equals(slot)) {
            rule.single = a;
        } else if ("long".equals(slot)) {
            rule.longPress = a;
        } else {
            rule.doublePress = a;
        }
        cfg.put(rule);
    }
}
