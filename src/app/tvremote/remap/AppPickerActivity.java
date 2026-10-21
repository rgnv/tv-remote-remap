package app.tvremote.remap;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installed apps that can be launched from a button. Loads off the UI thread. */
public class AppPickerActivity extends Activity {

    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_ACTIVITY = "activity";
    static final String EXTRA_LABEL = "label";

    private static final class Entry {
        String label;
        String pkg;
        String activity;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final LinearLayout body = Ui.column(this);
        final TextView loading = Ui.text(this, "Loading apps…", 15, Ui.MUTED, false);
        body.addView(loading);
        setContentView(Ui.scaffold(this, "Choose an app", null, body));

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Entry> apps = loadApps();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buildRows(body, apps);
                    }
                });
            }
        }, "app-picker").start();
    }

    private void buildRows(LinearLayout body, List<Entry> apps) {
        body.removeAllViews();
        if (apps.isEmpty()) {
            body.addView(Ui.text(this, "No launchable apps found", 15, Ui.MUTED, false));
            return;
        }
        for (final Entry e : apps) {
            Ui.Row row = Ui.row(this, "", e.label, e.pkg, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent data = new Intent();
                    data.putExtra(EXTRA_PKG, e.pkg);
                    data.putExtra(EXTRA_ACTIVITY, e.activity);
                    data.putExtra(EXTRA_LABEL, e.label);
                    setResult(RESULT_OK, data);
                    finish();
                }
            });
            if (e.icon != null) {
                row.badge.setVisibility(View.GONE);
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(e.icon);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        Ui.dp(this, 46), Ui.dp(this, 46));
                lp.rightMargin = Ui.dp(this, 16);
                row.addView(iv, 0, lp);
            }
            body.addView(row);
            body.addView(Ui.spacer(this, 8));
        }
        if (body.getChildCount() > 0) {
            body.getChildAt(0).requestFocus();
        }
    }

    private List<Entry> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, Entry> map = new LinkedHashMap<>();

        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent phone = new Intent(Intent.ACTION_MAIN);
        phone.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(leanback, 0));
        all.addAll(pm.queryIntentActivities(phone, 0));

        for (ResolveInfo ri : all) {
            if (ri.activityInfo == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || map.containsKey(pkg)) {
                continue;
            }
            Entry e = new Entry();
            e.pkg = pkg;
            e.activity = ri.activityInfo.name;
            e.label = String.valueOf(ri.loadLabel(pm));
            try {
                e.icon = ri.loadIcon(pm);
            } catch (Exception ignored) {
            }
            map.put(pkg, e);
        }

        List<Entry> list = new ArrayList<>(map.values());
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.toLowerCase().compareTo(b.label.toLowerCase());
            }
        });
        return list;
    }
}
