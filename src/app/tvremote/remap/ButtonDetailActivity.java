package app.tvremote.remap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** The three press types for one button, plus rename / remove. */
public class ButtonDetailActivity extends Activity {

    static final String EXTRA_SLOT = "slot";

    private int code;
    private ConfigStore cfg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        code = getIntent().getIntExtra(MainActivity.EXTRA_CODE, -1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = ConfigStore.get(this);
        KeyRule rule = cfg.ruleFor(code);
        if (rule == null) {
            finish();
            return;
        }

        LinearLayout body = Ui.column(this);
        body.addView(slot(rule, "single", "1", "Single press", "Tap once"));
        body.addView(Ui.spacer(this, 8));
        body.addView(slot(rule, "long", "L", "Long press", "Hold the button"));
        body.addView(Ui.spacer(this, 8));
        body.addView(slot(rule, "double", "2", "Double press", "Tap twice quickly"));

        body.addView(Ui.section(this, "Button"));
        body.addView(Ui.row(this, "✎", "Rename", rule.name,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rename();
                    }
                }));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.row(this, "✕", "Remove button",
                "Deletes the mapping and lets the key pass through again",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmRemove();
                    }
                }));

        setContentView(Ui.scaffold(this, rule.name, "Key code " + code, body));
        body.getChildAt(0).requestFocus();
    }

    private Ui.Row slot(KeyRule rule, final String slot, String badge, String title, String hint) {
        Action a;
        if ("single".equals(slot)) {
            a = rule.single;
        } else if ("long".equals(slot)) {
            a = rule.longPress;
        } else {
            a = rule.doublePress;
        }
        String summary = a == null || !a.isSet() ? hint : a.summary(this);
        Ui.Row row = Ui.row(this, badge, title, summary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ButtonDetailActivity.this, ActionPickerActivity.class);
                i.putExtra(MainActivity.EXTRA_CODE, code);
                i.putExtra(EXTRA_SLOT, slot);
                startActivity(i);
            }
        });
        if (a != null && a.isSet()) {
            row.badge.setTextColor(Ui.GREEN);
        }
        return row;
    }

    private void rename() {
        final KeyRule rule = cfg.ruleFor(code);
        if (rule == null) {
            return;
        }
        final EditText input = Ui.field(this, "Button name", rule.name);
        new AlertDialog.Builder(this)
                .setTitle("Rename button")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    rule.name = name.isEmpty() ? MainActivity.prettyName(code) : name;
                    cfg.put(rule);
                    onResume();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this)
                .setTitle("Remove this button?")
                .setMessage("The key will no longer be intercepted.")
                .setPositiveButton("Remove", (d, w) -> {
                    cfg.remove(code);
                    Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
