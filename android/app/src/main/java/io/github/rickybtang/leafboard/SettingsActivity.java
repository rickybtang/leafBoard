package io.github.rickybtang.leafboard;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.rickybtang.leafboard.model.Card;
import io.github.rickybtang.leafboard.storage.CardRepository;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SettingsActivity extends Activity {
    private static final Integer[] INTERVALS = {1, 3, 5, 10, 15, 30, 60};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsStore settings = new SettingsStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new EdgeSwipeBackScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(px(16), px(10), px(16), px(18));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView title = text("LeafBoard 设置", 24, true);
        form.addView(title, matchWrap());
        addSection(form, "同步间隔", "Leaf2 检查坚果云的频率；数据不变时不会重绘。 ");
        addLabel(form, "云端检查间隔");
        Spinner interval = new Spinner(this);
        List<String> intervalLabels = new ArrayList<>();
        for (Integer value : INTERVALS) intervalLabels.add(value + " 分钟");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, intervalLabels);
        interval.setAdapter(adapter);
        interval.setSelection(indexOf(settings.intervalMinutes()));
        interval.setMinimumHeight(px(42));
        interval.setBackground(outline());
        LinearLayout.LayoutParams intervalLayout = matchWrap();
        intervalLayout.bottomMargin = px(3);
        form.addView(interval, intervalLayout);
        addHelp(form, "可选 1～60 分钟；常规建议 3～15 分钟。", px(8));

        addSection(
                form,
                "卡片布局",
                "小 1×1｜中 2×1｜大 2×2（面积 1:2:4）\n"
                        + "顺序从 1 开始，按从左到右、从上到下排列。"
        );
        LayoutStore layoutStore = new LayoutStore(this);
        List<LayoutEditor> layoutEditors = new ArrayList<>();
        List<Card> cachedCards = new CardRepository(this).loadCatalogCards(settings.producerIds());
        if (cachedCards.isEmpty()) {
            addHelp(form, "尚未同步到已配置数据来源的云端卡片。请返回看板完成同步后再打开设置。", px(8));
        }
        for (int cardIndex = 0; cardIndex < cachedCards.size(); cardIndex++) {
            Card card = cachedCards.get(cardIndex);
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(px(10), px(6), px(10), px(9));
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.WHITE);
            border.setStroke(2, Color.BLACK);
            block.setBackground(border);

            Switch enabled = new Switch(this);
            enabled.setText(card.title);
            enabled.setTextSize(18);
            enabled.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            enabled.setMinimumHeight(px(40));
            enabled.setChecked(layoutStore.enabled(card));
            LinearLayout.LayoutParams enabledLayout = matchWrap();
            enabledLayout.bottomMargin = px(3);
            block.addView(enabled, enabledLayout);

            LinearLayout options = new LinearLayout(this);
            options.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout sizeGroup = new LinearLayout(this);
            sizeGroup.setOrientation(LinearLayout.VERTICAL);
            addLabel(sizeGroup, "大小");

            Spinner size = new Spinner(this);
            List<String> sizeLabels = new ArrayList<>();
            for (String value : card.allowedSizes) sizeLabels.add(sizeLabel(value));
            ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizeLabels);
            size.setAdapter(sizeAdapter);
            size.setSelection(Math.max(0, card.allowedSizes.indexOf(layoutStore.size(card))));
            size.setMinimumHeight(px(42));
            size.setBackground(outline());
            LinearLayout.LayoutParams sizeLayout = matchWrap();
            sizeGroup.addView(size, sizeLayout);
            LinearLayout.LayoutParams sizeGroupLayout = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2);
            sizeGroupLayout.rightMargin = px(8);
            options.addView(sizeGroup, sizeGroupLayout);

            LinearLayout orderGroup = new LinearLayout(this);
            orderGroup.setOrientation(LinearLayout.VERTICAL);
            addLabel(orderGroup, "顺序");
            int visibleOrder = Math.max(1, layoutStore.order(card, cardIndex) + 1);
            EditText order = input("填写正整数", String.valueOf(visibleOrder));
            order.setInputType(InputType.TYPE_CLASS_NUMBER);
            order.setGravity(Gravity.CENTER_VERTICAL);
            orderGroup.addView(order, matchWrap());
            options.addView(orderGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            block.addView(options, matchWrap());

            LinearLayout.LayoutParams blockLayout = matchWrap();
            blockLayout.bottomMargin = px(8);
            form.addView(block, blockLayout);
            layoutEditors.add(new LayoutEditor(card, enabled, size, card.allowedSizes, order));
        }

        addSection(form, "夜间暗屏", "设定时段内亮度降为 0，应用仍保持前台。 ");
        Switch quietEnabled = new Switch(this);
        quietEnabled.setText("启用夜间暗屏");
        quietEnabled.setTextSize(17);
        quietEnabled.setMinimumHeight(px(40));
        quietEnabled.setChecked(settings.quietEnabled());
        LinearLayout.LayoutParams quietLayout = matchWrap();
        quietLayout.bottomMargin = px(5);
        form.addView(quietEnabled, quietLayout);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        TimeField quietStart = addTimeField(timeRow, "开始", settings.quietStart());
        TimeField quietEnd = addTimeField(timeRow, "结束", settings.quietEnd());
        form.addView(timeRow, matchWrap());

        addSection(form, "连接配置", "低频设置：坚果云 WebDAV 与数据来源。 ");
        EditText url = addInput(
                form,
                "WebDAV 服务地址",
                "例如：https://dav.jianguoyun.com/dav/leafboard/",
                "请输入 HTTPS 地址",
                settings.webDavUrl()
        );
        EditText username = addInput(form, "坚果云账号", "登录邮箱", "账号邮箱", settings.username());
        EditText password = addInput(
                form,
                "坚果云应用密码",
                "留空表示保持现有应用密码。",
                "留空保持不变",
                ""
        );
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText producers = addInput(
                form,
                "数据来源 ID",
                "多个来源用英文逗号分隔。",
                "例如：example-producer",
                String.join(",", settings.producerIds())
        );

        addSection(form, "USB 导入（开发用）", "日常坚果云同步不需要此路径。 ");
        File inbox = new File(getExternalFilesDir(null), "inbox");
        TextView inboxPath = text(inbox.getAbsolutePath(), 13, false);
        inboxPath.setPadding(px(8), px(6), px(8), px(6));
        inboxPath.setBackground(outline());
        form.addView(inboxPath, matchWrap());

        Button save = new Button(this);
        save.setText("保存并返回看板");
        save.setTextSize(19);
        save.setTextColor(Color.WHITE);
        save.setMinimumHeight(px(50));
        save.setPadding(px(10), px(8), px(10), px(8));
        GradientDrawable saveBackground = new GradientDrawable();
        saveBackground.setColor(Color.BLACK);
        save.setBackground(saveBackground);
        save.setOnClickListener(view -> {
            try {
                LocalTime.parse(quietStart.value);
                LocalTime.parse(quietEnd.value);
                String urlValue = url.getText().toString().trim();
                if (!urlValue.startsWith("https://")) throw new IllegalArgumentException("WebDAV 必须使用 HTTPS");
                settings.saveConnection(
                        urlValue,
                        username.getText().toString(),
                        password.getText().toString()
                );
                settings.setProducerIds(producers.getText().toString());
                settings.setIntervalMinutes(INTERVALS[interval.getSelectedItemPosition()]);
                settings.setQuietHours(
                        quietEnabled.isChecked(),
                        quietStart.value,
                        quietEnd.value
                );
                Set<Integer> orders = new HashSet<>();
                for (LayoutEditor editor : layoutEditors) {
                    int orderValue = Integer.parseInt(editor.order.getText().toString().trim());
                    if (orderValue < 1) throw new IllegalArgumentException("显示顺序必须从 1 开始");
                    if (!orders.add(orderValue)) throw new IllegalArgumentException("显示顺序不能重复");
                    layoutStore.setEnabled(editor.card, editor.enabled.isChecked());
                    layoutStore.setSize(editor.card, editor.selectedSize());
                    layoutStore.setOrder(editor.card, orderValue - 1);
                }
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
                finish();
            } catch (Exception error) {
                Toast.makeText(this, error.getMessage() == null ? "设置保存失败" : error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams buttonLayout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLayout.setMargins(px(10), px(6), px(10), px(8));
        root.addView(save, buttonLayout);

        setContentView(root);
    }

    private EditText addInput(LinearLayout parent, String label, String help, String hint, String value) {
        addLabel(parent, label);
        if (!help.isEmpty()) addHelp(parent, help, px(3));
        EditText result = input(hint, value);
        LinearLayout.LayoutParams layout = matchWrap();
        layout.bottomMargin = px(10);
        parent.addView(result, layout);
        return result;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(17);
        input.setMinimumHeight(px(42));
        input.setSingleLine(true);
        input.setPadding(px(8), px(5), px(8), px(5));
        input.setBackground(outline());
        return input;
    }

    private void addSection(LinearLayout parent, String title, String description) {
        TextView heading = text(title, 20, true);
        LinearLayout.LayoutParams headingLayout = matchWrap();
        headingLayout.topMargin = px(14);
        headingLayout.bottomMargin = px(1);
        parent.addView(heading, headingLayout);
        addHelp(parent, description.trim(), px(7));
    }

    private void addLabel(LinearLayout parent, String value) {
        TextView label = text(value, 14, true);
        LinearLayout.LayoutParams layout = matchWrap();
        layout.topMargin = px(2);
        layout.bottomMargin = px(1);
        parent.addView(label, layout);
    }

    private void addHelp(LinearLayout parent, String value, int bottomMargin) {
        TextView help = text(value, 13, false);
        help.setTextColor(Color.DKGRAY);
        help.setLineSpacing(0f, 1.06f);
        LinearLayout.LayoutParams layout = matchWrap();
        layout.bottomMargin = bottomMargin;
        parent.addView(help, layout);
    }

    private TimeField addTimeField(LinearLayout parent, String label, String initialValue) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        addLabel(group, label);
        TimeField field = new TimeField(initialValue);
        field.button = new Button(this);
        field.button.setText(initialValue);
        field.button.setTextSize(17);
        field.button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        field.button.setMinimumHeight(px(42));
        field.button.setPadding(px(8), 0, px(8), 0);
        field.button.setBackground(outline());
        field.button.setOnClickListener(view -> {
            LocalTime initial = LocalTime.parse(field.value);
            new TimePickerDialog(
                    this,
                    (picker, hour, minute) -> {
                        field.value = String.format(Locale.US, "%02d:%02d", hour, minute);
                        field.button.setText(field.value);
                    },
                    initial.getHour(),
                    initial.getMinute(),
                    true
            ).show();
        });
        group.addView(field.button, matchWrap());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        layout.rightMargin = px(5);
        layout.bottomMargin = px(5);
        parent.addView(group, layout);
        return field;
    }

    private GradientDrawable outline() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setStroke(2, Color.BLACK);
        return drawable;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(px(2), px(2), px(2), px(2));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int indexOf(int value) {
        for (int index = 0; index < INTERVALS.length; index++) {
            if (INTERVALS[index] == value) return index;
        }
        return 3;
    }

    private static String sizeLabel(String value) {
        if ("small".equals(value)) return "小（1×1）";
        if ("medium".equals(value)) return "中（2×1）";
        if ("large".equals(value)) return "大（2×2）";
        return value;
    }

    private static final class LayoutEditor {
        final Card card;
        final Switch enabled;
        final Spinner size;
        final List<String> sizeValues;
        final EditText order;

        LayoutEditor(Card card, Switch enabled, Spinner size, List<String> sizeValues, EditText order) {
            this.card = card;
            this.enabled = enabled;
            this.size = size;
            this.sizeValues = sizeValues;
            this.order = order;
        }

        String selectedSize() {
            return sizeValues.get(size.getSelectedItemPosition());
        }
    }

    private static final class TimeField {
        String value;
        Button button;

        TimeField(String value) {
            this.value = value;
        }
    }

    private static final class EdgeSwipeBackScrollView extends ScrollView {
        private final Activity activity;
        private float downX;
        private float downY;

        EdgeSwipeBackScrollView(Activity activity) {
            super(activity);
            this.activity = activity;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                float deltaX = event.getX() - downX;
                float deltaY = event.getY() - downY;
                boolean fromLeftEdge = downX <= getWidth() * 0.14f;
                boolean swipeRight = deltaX > 120f && deltaX > Math.abs(deltaY) * 1.2f;
                if (fromLeftEdge && swipeRight) {
                    activity.finish();
                    return true;
                }
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
