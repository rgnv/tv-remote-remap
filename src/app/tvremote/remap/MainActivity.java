package app.tvremote.remap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Home screen: configured buttons, add-button capture, settings. */
public class MainActivity extends Activity {

    static final String EXTRA_CODE = "code";

    private ConfigStore cfg;
    private LinearLayout body;
    private int lastFocusCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        maybeImport();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = ConfigStore.get(this);
        rebuild();
    }

    private void rebuild() {
        body = Ui.column(this);

        boolean active = serviceEnabled();
        Ui.Row status = Ui.row(this, active ? "●" : "○", "Accessibility service",
                active ? "Active — this app is filtering the configured keys"
                        : "Off — select to enable, then turn on \"TV Remote Remap\"",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                });
        status.badge.setTextColor(active ? Ui.GREEN : Ui.RED);
        body.addView(status);

        body.addView(Ui.section(this, "Buttons"));
        if (cfg.rules().isEmpty()) {
            body.addView(Ui.text(this,
                    "No buttons yet. Select \"Add a button\" and press any key on the remote.",
                    15, Ui.MUTED, false));
        }
        for (final KeyRule rule : cfg.rules()) {
            Ui.Row row = Ui.row(this, initials(rule.name), rule.name, rule.summary(MainActivity.this),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            open(rule.code);
                        }
                    });
            row.setTag(rule.code);
            body.addView(row);
            body.addView(Ui.spacer(this, 8));
        }

        body.addView(Ui.spacer(this, 4));
        body.addView(Ui.row(this, "+", "Add a button",
                "Press a key on the remote to capture it", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        capture();
                    }
                }));

        body.addView(Ui.section(this, "App"));
        body.addView(Ui.row(this, "⚙", "Settings",
                "Long-press timing, double-press window, import / export",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    }
                }));

        setContentView(Ui.scaffold(this, "TV Remote Remap",
                "Short press · Long press · Double press", body));

        View target = lastFocusCode == 0 ? null : body.findViewWithTag(lastFocusCode);
        if (target == null) {
            target = body.getChildAt(0);
        }
        if (target != null) {
            target.requestFocus();
        }
    }

    private boolean serviceEnabled() {
        boolean master = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return master && enabled != null && enabled.contains(getPackageName() + "/");
    }

    @Override
    protected void onPause() {
        super.onPause();
        CaptureBus.stop(); // never keep swallowing keys behind another app
    }

    private void open(int code) {
        lastFocusCode = code;
        Intent i = new Intent(this, ButtonDetailActivity.class);
        i.putExtra(EXTRA_CODE, code);
        startActivity(i);
    }

    private void capture() {
        if (!serviceEnabled()) {
            Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show();
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Press a button")
                .setMessage("Point the remote at the TV and press the button you want to remap.")
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnDismissListener(d -> CaptureBus.stop());
        dialog.show();

        CaptureBus.start(code -> runOnUiThread(() -> {
            dialog.dismiss();
            if (Keys.isNavigationKey(code)) {
                Toast.makeText(this, "That key is needed to navigate — not remapped",
                        Toast.LENGTH_LONG).show();
            } else if (cfg.ruleFor(code) != null) {
                Toast.makeText(this, "That button is already configured", Toast.LENGTH_SHORT).show();
            } else {
                cfg.put(new KeyRule(code, prettyName(code)));
            }
            open(code);
        }));
    }

    static String prettyName(int code) {
        String raw = KeyEvent.keyCodeToString(code);
        if (raw.startsWith("KEYCODE_")) {
            raw = raw.substring("KEYCODE_".length());
        }
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.length() == 0 ? "Button " + code : sb.toString();
    }

    static String initials(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() < 2 && !p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
            }
        }
        return sb.toString();
    }

    /** One-shot bulk config load: drop import.json into the app's files dir. */
    private void maybeImport() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            return;
        }
        File in = new File(dir, "import.json");
        if (!in.exists()) {
            return;
        }
        try {
            String json = readFile(in);
            ConfigStore.get(this).importJson(json);
            File done = new File(dir, "imported.json");
            if (!in.renameTo(done)) {
                //noinspection ResultOfMethodCallIgnored
                in.delete(); // never re-apply a stale import on every launch
            }
            Toast.makeText(this, "Config imported", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static final int MAX_IMPORT_BYTES = 1_048_576;

    private static String readFile(File f) throws Exception {
        if (f.length() > MAX_IMPORT_BYTES) {
            throw new IllegalArgumentException("file too large");
        }
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
