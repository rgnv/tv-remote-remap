package app.tvremote.remap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/** Generic D-pad list picker: returns the chosen index. */
public class ListPickerActivity extends Activity {

    static final String EXTRA_TITLE = "title";
    static final String EXTRA_ITEMS = "items";
    static final String EXTRA_SELECTED = "selected";
    static final String EXTRA_INDEX = "index";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String[] items = getIntent().getStringArrayExtra(EXTRA_ITEMS);
        int selected = getIntent().getIntExtra(EXTRA_SELECTED, -1);

        LinearLayout body = Ui.column(this);
        Ui.Row selectedRow = null;
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                final int index = i;
                Ui.Row row = Ui.row(this, selected == i ? "✓" : String.valueOf(i + 1),
                        items[i], null, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent data = new Intent();
                                data.putExtra(EXTRA_INDEX, index);
                                setResult(RESULT_OK, data);
                                finish();
                            }
                        });
                if (selected == i) {
                    row.badge.setTextColor(Ui.GREEN);
                    selectedRow = row;
                }
                body.addView(row);
                body.addView(Ui.spacer(this, 8));
            }
        }
        setContentView(Ui.scaffold(this, title == null ? "Choose" : title, null, body));
        View target = selectedRow != null ? selectedRow
                : (body.getChildCount() > 0 ? body.getChildAt(0) : null);
        if (target != null) {
            target.requestFocus();
        }
    }
}
