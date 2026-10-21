package app.tvremote.remap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-action parameters. Each value is a list row that opens an edit dialog —
 * inline EditTexts trap D-pad focus behind the IME on TV.
 */
public class ActionConfigActivity extends Activity {

    static final String EXTRA_TYPE = "type";
    private static final int REQ_METHOD = 41;

    private static final String[] METHODS = {"POST", "GET", "PUT", "PATCH", "DELETE"};

    private int code;
    private String slot;
    private String type;

    private final Map<String, String> values = new LinkedHashMap<>();
    private boolean loaded;
    private String method = "POST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        code = getIntent().getIntExtra(MainActivity.EXTRA_CODE, -1);
        slot = getIntent().getStringExtra(ButtonDetailActivity.EXTRA_SLOT);
        type = getIntent().getStringExtra(EXTRA_TYPE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeyRule rule = ConfigStore.get(this).ruleFor(code);
        if (rule == null) {
            finish();
            return;
        }
        Action existing = "single".equals(slot) ? rule.single
                : "long".equals(slot) ? rule.longPress : rule.doublePress;
        boolean sameType = existing != null && type.equals(existing.type);
        if (sameType && !existing.get("method").isEmpty()) {
            method = existing.get("method");
        }
        if (!loaded) {
            // Load once: refilling on every onResume resurrects values the user cleared.
            for (String k : keysForType()) {
                values.put(k, sameType ? existing.get(k) : "");
            }
            loaded = true;
        }

        LinearLayout bodyColumn = Ui.column(this);
        switch (type) {
            case Action.URL:
                bodyColumn.addView(param("url", "URL", "https://example.com", false));
                break;
            case Action.HTTP:
                bodyColumn.addView(param("url", "URL", "https://ha.local/api/webhook/abc", false));
                bodyColumn.addView(methodRow());
                bodyColumn.addView(param("headers", "Headers (one per line)",
                        "Authorization: Bearer ...", true));
                bodyColumn.addView(param("body", "Body", "{\"state\":\"on\"}", true));
                break;
            case Action.HA:
                bodyColumn.addView(param("base", "Base URL", "http://homeassistant.local:8123", false));
                bodyColumn.addView(param("token", "Token", "Long-lived access token", false));
                bodyColumn.addView(param("service", "Service (domain.name)", "light.toggle", false));
                bodyColumn.addView(param("entity", "Entity ID (optional)", "light.living_room", false));
                bodyColumn.addView(param("data", "Extra JSON (optional)", "{\"brightness\": 128}", true));
                break;
            case Action.VOICE:
                bodyColumn.addView(param("pkg", "Assistant package (optional)",
                        "com.google.android.katniss", false));
                break;
            case Action.INTENT:
                bodyColumn.addView(param("uri", "Intent URI", "intent://...#Intent;...;end", false));
                break;
            default:
                break;
        }

        bodyColumn.addView(Ui.spacer(this, 16));
        bodyColumn.addView(Ui.row(this, "✓", "Save", "Store this action",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        save();
                    }
                }));
        bodyColumn.addView(Ui.spacer(this, 8));
        bodyColumn.addView(Ui.row(this, "▶", "Test", "Run the action once from here",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        test();
                    }
                }));

        setContentView(Ui.scaffold(this, title(), subtitle(), bodyColumn));
        bodyColumn.getChildAt(0).requestFocus();
    }

    private String[] keysForType() {
        switch (type) {
            case Action.URL:
                return new String[]{"url"};
            case Action.HTTP:
                return new String[]{"url", "headers", "body"};
            case Action.HA:
                return new String[]{"base", "token", "service", "entity", "data"};
            case Action.VOICE:
                return new String[]{"pkg"};
            case Action.INTENT:
                return new String[]{"uri"};
            default:
                return new String[0];
        }
    }

    private String title() {
        switch (type) {
            case Action.URL:
                return "Open URL";
            case Action.HTTP:
                return "Webhook";
            case Action.HA:
                return "Home Assistant";
            case Action.VOICE:
                return "Voice assistant";
            case Action.INTENT:
                return "Intent URI";
            default:
                return "Action";
        }
    }

    private String subtitle() {
        return "single".equals(slot) ? "Single press"
                : "long".equals(slot) ? "Long press" : "Double press";
    }

    /** One parameter: shows the value, opens a dialog to edit it. */
    private Ui.Row param(final String key, final String label, final String hint,
                         final boolean multiline) {
        final boolean secret = "token".equals(key) || "headers".equals(key);
        final Ui.Row row = Ui.row(this, "✎", label, display(key, hint, secret), null);
        row.setTag(key);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = multiline
                        ? Ui.multiline(ActionConfigActivity.this, hint, values.get(key))
                        : Ui.field(ActionConfigActivity.this, hint, values.get(key));
                if (secret) {
                    input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                new AlertDialog.Builder(ActionConfigActivity.this)
                        .setTitle(label)
                        .setView(input)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                values.put(key, input.getText().toString().trim());
                                row.text(label, display(key, hint, secret));
                                row.badge.setTextColor(values.get(key).isEmpty()
                                        ? Ui.ACCENT : Ui.GREEN);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        if (!values.get(key).isEmpty()) {
            row.badge.setTextColor(Ui.GREEN);
        }
        return row;
    }

    private String display(String key, String hint, boolean secret) {
        String value = values.get(key);
        if (value.isEmpty()) {
            return hint;
        }
        return secret ? "•••••••• (set)" : value;
    }

    private View methodRow() {
        final Ui.Row row = Ui.row(this, "⋯", "Method", method, null);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ActionConfigActivity.this, ListPickerActivity.class);
                i.putExtra(ListPickerActivity.EXTRA_TITLE, "HTTP method");
                i.putExtra(ListPickerActivity.EXTRA_ITEMS, METHODS);
                int selected = 0;
                for (int k = 0; k < METHODS.length; k++) {
                    if (METHODS[k].equals(method)) {
                        selected = k;
                    }
                }
                i.putExtra(ListPickerActivity.EXTRA_SELECTED, selected);
                startActivityForResult(i, REQ_METHOD);
            }
        });
        return row;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_METHOD && resultCode == RESULT_OK && data != null) {
            int idx = data.getIntExtra(ListPickerActivity.EXTRA_INDEX, 0);
            method = METHODS[Math.max(0, Math.min(idx, METHODS.length - 1))];
            onResume();
        }
    }

    private Action build() {
        Action a = new Action(type);
        switch (type) {
            case Action.URL:
                a.set("url", values.get("url"));
                break;
            case Action.HTTP:
                a.set("url", values.get("url"))
                        .set("method", method)
                        .set("headers", values.get("headers"))
                        .set("body", values.get("body"));
                break;
            case Action.HA:
                a.set("base", values.get("base"))
                        .set("token", values.get("token"))
                        .set("service", values.get("service"))
                        .set("entity", values.get("entity"))
                        .set("data", values.get("data"));
                break;
            case Action.VOICE:
                a.set("pkg", values.get("pkg"));
                break;
            case Action.INTENT:
                a.set("uri", values.get("uri"));
                break;
            default:
                break;
        }
        return a;
    }

    private boolean valid(Action a) {
        switch (type) {
            case Action.URL:
            case Action.HTTP:
                return !a.get("url").isEmpty();
            case Action.HA:
                return !a.get("base").isEmpty() && !a.get("service").isEmpty();
            case Action.INTENT:
                return !a.get("uri").isEmpty();
            default:
                return true;
        }
    }

    private void save() {
        Action a = build();
        if (!valid(a)) {
            Toast.makeText(this, "Fill in the required fields", Toast.LENGTH_SHORT).show();
            return;
        }
        KeyRule rule = ConfigStore.get(this).ruleFor(code);
        if (rule == null) {
            finish();
            return;
        }
        if ("single".equals(slot)) {
            rule.single = a;
        } else if ("long".equals(slot)) {
            rule.longPress = a;
        } else {
            rule.doublePress = a;
        }
        ConfigStore.get(this).put(rule);
        setResult(RESULT_OK);
        finish();
    }

    private void test() {
        Action a = build();
        if (!valid(a)) {
            Toast.makeText(this, "Fill in the required fields", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Action.HTTP.equals(type)) {
                HttpTask.send(a.get("method"), a.get("url"), a.get("headers"), a.get("body"));
                Toast.makeText(this, "Request sent - result in logcat", Toast.LENGTH_SHORT).show();
            } else if (Action.HA.equals(type)) {
                Action.HaRequest req = a.buildHaRequest();
                HttpTask.send("POST", req.url, req.headers, req.body);
                Toast.makeText(this, "Service called - result in logcat", Toast.LENGTH_SHORT).show();
            } else if (Action.URL.equals(type) || Action.INTENT.equals(type)) {
                Intent i = a.buildIntent();
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } else if (Action.VOICE.equals(type)) {
                Intent i = new Intent(Intent.ACTION_VOICE_COMMAND);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "No assistant on this device", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
