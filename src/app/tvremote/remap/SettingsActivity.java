package app.tvremote.remap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Timings, config import/export, about. */
public class SettingsActivity extends Activity {

    private static final int REQ_LONG = 51;
    private static final int REQ_DOUBLE = 52;

    private static final int[] LONG_MS = {300, 400, 500, 700, 900, 1200};
    private static final int[] DOUBLE_MS = {200, 250, 300, 350, 400, 500};

    private ConfigStore cfg;

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        cfg = ConfigStore.get(this);

        LinearLayout body = Ui.column(this);
        boolean active = serviceEnabled();
        Ui.Row status = Ui.row(this, active ? "●" : "○", "Accessibility service",
                active ? "Active" : "Off — select to enable", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                });
        status.badge.setTextColor(active ? Ui.GREEN : Ui.RED);
        body.addView(status);

        body.addView(Ui.section(this, "Timing"));
        body.addView(timingRow("Long press", cfg.longPressMs() + " ms", LONG_MS, REQ_LONG));
        body.addView(Ui.spacer(this, 8));
        body.addView(timingRow("Double press window", cfg.doublePressMs() + " ms", DOUBLE_MS, REQ_DOUBLE));

        body.addView(Ui.section(this, "Configuration"));
        body.addView(Ui.row(this, "↓", "Import config",
                "Read import.json from this app's files folder", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        importConfig();
                    }
                }));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.row(this, "↑", "Export config",
                "Write config.json to the app's files folder", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        exportConfig();
                    }
                }));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.row(this, "⟲", "Reset all mappings",
                "Delete every button and action", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmReset();
                    }
                }));

        body.addView(Ui.section(this, "About"));
        body.addView(Ui.row(this, "i", "TV Remote Remap", "Version " + version(), null));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.row(this, "↗", "Source code",
                "github.com/rgnv/tv-remote-remap", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/rgnv/tv-remote-remap")));
                        } catch (Exception ignored) {
                        }
                    }
                }));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.row(this, "A", "How it works",
                "Accessibility key filter — no root, survives reboot", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("How it works")
                                .setMessage("TV Remote Remap runs an accessibility service that "
                                        + "asks Android to deliver key events before apps see them. "
                                        + "Keys you configure are consumed; everything else passes "
                                        + "through. Short press runs on release, long press fires "
                                        + "while held, double press fires on the second tap.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                }));

        setContentView(Ui.scaffold(this, "Settings", null, body));
        if (body.getChildCount() > 0) {
            body.getChildAt(0).requestFocus();
        }
    }

    private Ui.Row timingRow(String title, String value, final int[] values, final int req) {
        final int current = Integer.parseInt(value.replace(" ms", ""));
        return Ui.row(this, "⏱", title, value, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] labels = new String[values.length];
                int selected = 0;
                for (int i = 0; i < values.length; i++) {
                    labels[i] = values[i] + " ms";
                    if (values[i] == current) {
                        selected = i;
                    }
                }
                Intent i = new Intent(SettingsActivity.this, ListPickerActivity.class);
                i.putExtra(ListPickerActivity.EXTRA_TITLE, title);
                i.putExtra(ListPickerActivity.EXTRA_ITEMS, labels);
                i.putExtra(ListPickerActivity.EXTRA_SELECTED, selected);
                startActivityForResult(i, req);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        int idx = data.getIntExtra(ListPickerActivity.EXTRA_INDEX, -1);
        if (idx < 0) {
            return;
        }
        if (requestCode == REQ_LONG) {
            cfg.setTimings(LONG_MS[Math.min(idx, LONG_MS.length - 1)], cfg.doublePressMs());
        } else if (requestCode == REQ_DOUBLE) {
            cfg.setTimings(cfg.longPressMs(), DOUBLE_MS[Math.min(idx, DOUBLE_MS.length - 1)]);
        }
        rebuild();
    }

    private boolean serviceEnabled() {
        boolean master = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return master && enabled != null && enabled.contains(getPackageName() + "/");
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private File dir() {
        File d = getExternalFilesDir(null);
        if (d == null) {
            d = getFilesDir();
        }
        return d;
    }

    private void importConfig() {
        File src = new File(dir(), "import.json");
        if (!src.exists()) {
            Toast.makeText(this, "No import.json in " + dir(), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (src.length() > 1_048_576) {
                Toast.makeText(this, "Config file too large", Toast.LENGTH_LONG).show();
                return;
            }
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            String json = new String(out.toByteArray(), StandardCharsets.UTF_8);
            cfg.importJson(json);
            Toast.makeText(this, "Imported " + cfg.rules().size() + " buttons", Toast.LENGTH_SHORT).show();
            rebuild();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportConfig() {
        new AlertDialog.Builder(this)
                .setTitle("Export configuration?")
                .setMessage("config.json contains your Home Assistant token and webhook "
                        + "headers in plain text. Anyone who can read that file can use them.")
                .setPositiveButton("Export", (d, w) -> writeExport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void writeExport() {
        try {
            File out = new File(dir(), "config.json");
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(cfg.toJson().getBytes(StandardCharsets.UTF_8));
            fos.close();
            Toast.makeText(this, "Wrote " + out, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset all mappings?")
                .setMessage("Every button and action will be deleted.")
                .setPositiveButton("Reset", (d, w) -> {
                    cfg.clear();
                    Toast.makeText(this, "Mappings cleared", Toast.LENGTH_SHORT).show();
                    rebuild();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
