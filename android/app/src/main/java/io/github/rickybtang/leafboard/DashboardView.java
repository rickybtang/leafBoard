package io.github.rickybtang.leafboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import io.github.rickybtang.leafboard.model.Card;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DashboardView extends View {
    private static final int COLUMNS = 4;
    private static final int ROWS = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LayoutStore layoutStore;
    private List<Card> cards = Collections.emptyList();
    private List<List<Placement>> pages = Collections.singletonList(Collections.emptyList());
    private int pageIndex = 0;
    private String syncStatus = "等待同步";
    private String lastUpdated = "";
    private boolean quietMode = false;
    private float downX;
    private float downY;
    private Runnable refreshAction;
    private Runnable settingsAction;
    private Runnable homeAction;

    public DashboardView(Context context) {
        super(context);
        layoutStore = new LayoutStore(context);
        setBackgroundColor(Color.WHITE);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
    }

    public void setActions(Runnable refreshAction, Runnable settingsAction, Runnable homeAction) {
        this.refreshAction = refreshAction;
        this.settingsAction = settingsAction;
        this.homeAction = homeAction;
    }

    public void setCards(List<Card> cards) {
        this.cards = new ArrayList<>(cards);
        Map<String, Integer> fallbackOrder = new HashMap<>();
        for (int index = 0; index < this.cards.size(); index++) {
            fallbackOrder.put(this.cards.get(index).ref(), index);
        }
        this.cards.sort((left, right) -> {
            int leftFallback = fallbackOrder.getOrDefault(left.ref(), 0);
            int rightFallback = fallbackOrder.getOrDefault(right.ref(), 0);
            int result = Integer.compare(layoutStore.order(left, leftFallback), layoutStore.order(right, rightFallback));
            return result != 0 ? result : left.ref().compareTo(right.ref());
        });
        rebuildPages();
        invalidate();
    }

    public void setSyncStatus(String status, String updated) {
        this.syncStatus = status;
        this.lastUpdated = updated;
        invalidate();
    }

    public void setQuietMode(boolean quiet) {
        if (quietMode != quiet) {
            quietMode = quiet;
            invalidate();
        }
    }

    public void nextPage() {
        if (pages.size() > 1) {
            pageIndex = (pageIndex + 1) % pages.size();
            invalidate();
        }
    }

    public void previousPage() {
        if (pages.size() > 1) {
            pageIndex = (pageIndex - 1 + pages.size()) % pages.size();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        rebuildPages();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);
        if (quietMode) {
            drawQuiet(canvas);
            return;
        }

        float width = getWidth();
        float footerHeight = Math.max(72f, getHeight() * 0.055f);
        float margin = 28f;
        float gap = 18f;
        float gridTop = margin;
        float gridHeight = getHeight() - margin - footerHeight;
        float cellWidth = (width - margin * 2 - gap * (COLUMNS - 1)) / COLUMNS;
        float cellHeight = (gridHeight - gap * (ROWS - 1)) / ROWS;

        List<Placement> current = pages.get(Math.min(pageIndex, pages.size() - 1));
        for (Placement placement : current) {
            float left = margin + placement.column * (cellWidth + gap);
            float top = gridTop + placement.row * (cellHeight + gap);
            float right = left + placement.columnSpan * cellWidth + (placement.columnSpan - 1) * gap;
            float bottom = top + placement.rowSpan * cellHeight + (placement.rowSpan - 1) * gap;
            drawCard(canvas, placement.card, placement.size, new RectF(left, top, right, bottom));
        }

        drawFooter(canvas, width, getHeight() - footerHeight, footerHeight);
    }

    private void drawCard(Canvas canvas, Card card, String size, RectF bounds) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.BLACK);
        canvas.drawRect(bounds, paint);
        paint.setStyle(Paint.Style.FILL);

        float padding = "small".equals(size) ? 16f : "large".equals(size) ? 24f : 20f;
        float x = bounds.left + padding;
        float titleSize = "small".equals(size) ? 28f : "large".equals(size) ? 40f : 36f;
        float y = bounds.top + padding + titleSize;
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(titleSize);
        boolean expired = isExpired(card);
        float titleWidth = bounds.width() - padding * 2 - (expired ? 90f : 0f);
        drawFittedText(canvas, card.title, x, y, titleWidth);

        if (expired) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(24f);
            canvas.drawText("已过期", bounds.right - padding, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        float contentTop = y + ("small".equals(size) ? 14f : 18f);
        if ("list".equals(card.type)) {
            drawList(canvas, card, size, x, contentTop, bounds);
        } else {
            drawFields(canvas, card, size, x, contentTop, bounds);
        }
    }

    private void drawFields(Canvas canvas, Card card, String size, float x, float top, RectF bounds) {
        List<Card.Field> fields = card.visibleFields(size);
        Card.Field primary = null;
        for (Card.Field field : fields) {
            if ("primary".equals(field.role)) {
                primary = field;
                break;
            }
        }
        if (primary == null && !fields.isEmpty()) primary = fields.get(0);

        List<Card.Field> details = new ArrayList<>();
        for (Card.Field field : fields) {
            if (field != primary) details.add(field);
        }

        String primaryLabel = primary == null ? "" : primary.label;
        String primaryValue = primary == null ? card.state : primary.displayValue();
        if (primaryValue == null || primaryValue.isEmpty()) return;

        if ("small".equals(size)) {
            drawSmallFields(canvas, primaryValue, details, x, top, bounds);
        } else if ("medium".equals(size)) {
            drawMediumFields(canvas, primaryLabel, primaryValue, details, x, top, bounds);
        } else {
            drawLargeFields(canvas, primaryLabel, primaryValue, details, x, top, bounds);
        }
    }

    private void drawSmallFields(Canvas canvas, String primaryValue, List<Card.Field> details, float x, float top, RectF bounds) {
        float right = bounds.right - 16f;
        float bottom = bounds.bottom - 16f;
        int detailCount = Math.min(3, details.size());
        float primaryBottom = detailCount == 0 ? bottom : Math.min(top + 122f, top + (bottom - top) * 0.43f);
        drawBillboard(canvas, "", primaryValue, new RectF(x, top, right, primaryBottom), false, 64f, 52f, 20f);
        if (detailCount == 0) return;

        drawDivider(canvas, x, primaryBottom, right, primaryBottom, 2f);
        float rowHeight = (bottom - primaryBottom) / detailCount;
        for (int index = 0; index < detailCount; index++) {
            float rowTop = primaryBottom + rowHeight * index;
            float rowBottom = rowTop + rowHeight;
            drawKeyValueRow(canvas, details.get(index), x, right, rowTop, rowBottom, 18f, 26f, 0.42f, true);
            if (index + 1 < detailCount) drawDivider(canvas, x, rowBottom, right, rowBottom, 1f);
        }
    }

    private void drawMediumFields(Canvas canvas, String primaryLabel, String primaryValue, List<Card.Field> details, float x, float top, RectF bounds) {
        float right = bounds.right - 20f;
        float bottom = bounds.bottom - 20f;
        float split = x + (right - x) * 0.42f;
        drawBillboard(canvas, primaryLabel, primaryValue, new RectF(x, top, split - 16f, bottom), true, 64f, 52f, 22f);
        drawDivider(canvas, split, top, split, bottom, 2f);

        int detailCount = Math.min(3, details.size());
        if (detailCount == 0) return;
        float rowLeft = split + 18f;
        float rowHeight = (bottom - top) / detailCount;
        for (int index = 0; index < detailCount; index++) {
            float rowTop = top + rowHeight * index;
            float rowBottom = rowTop + rowHeight;
            drawKeyValueRow(canvas, details.get(index), rowLeft, right, rowTop, rowBottom, 22f, 28f, 0.45f, true);
            if (index + 1 < detailCount) drawDivider(canvas, rowLeft, rowBottom, right, rowBottom, 1f);
        }
    }

    private void drawLargeFields(Canvas canvas, String primaryLabel, String primaryValue, List<Card.Field> details, float x, float top, RectF bounds) {
        float right = bounds.right - 24f;
        float bottom = bounds.bottom - 24f;
        float primaryBottom = Math.min(top + 154f, top + (bottom - top) * 0.28f);
        drawBillboard(canvas, primaryLabel, primaryValue, new RectF(x, top, right, primaryBottom), true, 64f, 52f, 26f);
        drawDivider(canvas, x, primaryBottom, right, primaryBottom, 3f);

        int detailCount = Math.min(6, details.size());
        if (detailCount == 0) return;
        float rowHeight = (bottom - primaryBottom) / detailCount;
        for (int index = 0; index < detailCount; index++) {
            float rowTop = primaryBottom + rowHeight * index;
            float rowBottom = rowTop + rowHeight;
            drawKeyValueRow(canvas, details.get(index), x, right, rowTop, rowBottom, 30f, 36f, 0.40f, false);
            if (index + 1 < detailCount) drawDivider(canvas, x, rowBottom, right, rowBottom, 1f);
        }
    }

    private void drawBillboard(Canvas canvas, String label, String value, RectF area, boolean showLabel, float targetSize, float minimumSize, float labelSize) {
        paint.setTextAlign(Paint.Align.LEFT);
        if (showLabel && label != null && !label.isEmpty()) {
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(labelSize);
            canvas.drawText(label, area.left, area.top + labelSize, paint);
        }
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(targetSize);
        float maxWidth = area.width();
        float measured = paint.measureText(value);
        if (measured > maxWidth && measured > 0f) {
            paint.setTextSize(Math.max(minimumSize, targetSize * maxWidth / measured));
        }
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float labelOffset = showLabel ? labelSize + 8f : 0f;
        float centerY = area.top + labelOffset + (area.height() - labelOffset) / 2f;
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        drawFittedText(canvas, value, area.left, baseline, maxWidth);
    }

    private void drawKeyValueRow(Canvas canvas, Card.Field field, float left, float right, float top, float bottom, float labelSize, float valueSize, float labelRatio, boolean compactValue) {
        if (field.secondary != null) {
            drawDualValueRow(canvas, field, left, right, top, bottom);
            return;
        }
        float width = right - left;
        float gap = 10f;
        float labelWidth = Math.max(0f, width * labelRatio - gap);
        float valueWidth = Math.max(0f, width - labelWidth - gap);

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(labelSize);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = (top + bottom) / 2f - (metrics.ascent + metrics.descent) / 2f;
        paint.setTextAlign(Paint.Align.LEFT);
        drawFittedText(canvas, field.label, left, baseline, labelWidth);

        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(valueSize);
        metrics = paint.getFontMetrics();
        baseline = (top + bottom) / 2f - (metrics.ascent + metrics.descent) / 2f;
        paint.setTextAlign(Paint.Align.RIGHT);
        drawFittedText(canvas, compactValue ? field.compactDisplayValue() : field.displayValue(), right, baseline, valueWidth);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDualValueRow(Canvas canvas, Card.Field field, float left, float right, float top, float bottom) {
        float width = right - left;
        float labelRight = left + width * 0.30f;
        float firstRight = left + width * 0.72f;
        float gap = 10f;

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        drawCenteredFittedText(canvas, field.label, left, top, bottom, labelRight - left - gap, Paint.Align.LEFT, 26f, 22f);

        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        drawCenteredFittedText(canvas, field.displayValue(), labelRight, top, bottom, firstRight - labelRight - gap, Paint.Align.LEFT, 30f, 24f);
        drawCenteredFittedText(canvas, field.secondary.displayValue(), right, top, bottom, right - firstRight, Paint.Align.RIGHT, 30f, 24f);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCenteredFittedText(Canvas canvas, String value, float x, float top, float bottom, float maxWidth, Paint.Align align, float targetSize, float minimumSize) {
        paint.setTextAlign(align);
        paint.setTextSize(targetSize);
        float measured = paint.measureText(value);
        if (measured > maxWidth && measured > 0f) {
            paint.setTextSize(Math.max(minimumSize, targetSize * maxWidth / measured));
        }
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = (top + bottom) / 2f - (metrics.ascent + metrics.descent) / 2f;
        drawFittedText(canvas, value, x, baseline, maxWidth);
    }

    private void drawDivider(Canvas canvas, float startX, float startY, float endX, float endY, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(Color.BLACK);
        canvas.drawLine(startX, startY, endX, endY, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawList(Canvas canvas, Card card, String size, float x, float top, RectF bounds) {
        int limit = "small".equals(size) ? 2 : "medium".equals(size) ? 4 : 8;
        float lineHeight = Math.min(72f, Math.max(44f, (bounds.bottom - top - 12f) / Math.max(1, Math.min(limit, card.items.size()))));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.min(34f, lineHeight * 0.55f));
        for (int index = 0; index < Math.min(limit, card.items.size()); index++) {
            Card.ListItem item = card.items.get(index);
            String prefix = item.checked ? "✓ " : "□ ";
            drawFittedText(canvas, prefix + item.text, x, top + lineHeight * (index + 0.72f), bounds.right - x - 16f);
        }
        if (card.items.isEmpty()) {
            canvas.drawText("暂无内容", x, top + 54f, paint);
        }
    }

    private void drawFooter(Canvas canvas, float width, float top, float height) {
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(3f);
        canvas.drawLine(28f, top + 4f, width - 28f, top + 4f, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(26f);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("首页", 32f, top + height * 0.68f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        String page = "第 " + (pageIndex + 1) + "/" + pages.size() + " 页";
        canvas.drawText(page + " · " + syncStatus, width / 2f, top + height * 0.48f, paint);
        if (!lastUpdated.isEmpty()) {
            paint.setTextSize(21f);
            canvas.drawText(lastUpdated, width / 2f, top + height * 0.82f, paint);
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(26f);
        canvas.drawText("立即刷新", width - 32f, top + height * 0.68f, paint);
    }

    private void drawQuiet(Canvas canvas) {
        paint.setColor(Color.BLACK);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(28f);
        canvas.drawText("夜间模式", getWidth() / 2f, getHeight() / 2f, paint);
    }

    private void rebuildPages() {
        List<List<Placement>> result = new ArrayList<>();
        boolean[][] occupied = new boolean[ROWS][COLUMNS];
        List<Placement> current = new ArrayList<>();
        for (Card card : cards) {
            if (!layoutStore.enabled(card)) continue;
            String size = layoutStore.size(card);
            int colSpan = "small".equals(size) ? 1 : 2;
            int rowSpan = "large".equals(size) ? 2 : 1;
            int[] slot = findSlot(occupied, colSpan, rowSpan);
            if (slot == null) {
                result.add(current);
                current = new ArrayList<>();
                occupied = new boolean[ROWS][COLUMNS];
                slot = findSlot(occupied, colSpan, rowSpan);
            }
            occupy(occupied, slot[0], slot[1], colSpan, rowSpan);
            current.add(new Placement(card, size, slot[0], slot[1], colSpan, rowSpan));
        }
        if (!current.isEmpty() || result.isEmpty()) result.add(current);
        pages = result;
        if (pageIndex >= pages.size()) pageIndex = 0;
    }

    private static int[] findSlot(boolean[][] occupied, int colSpan, int rowSpan) {
        for (int row = 0; row <= ROWS - rowSpan; row++) {
            for (int column = 0; column <= COLUMNS - colSpan; column++) {
                boolean free = true;
                for (int y = row; y < row + rowSpan; y++) {
                    for (int x = column; x < column + colSpan; x++) free &= !occupied[y][x];
                }
                if (free) return new int[]{column, row};
            }
        }
        return null;
    }

    private static void occupy(boolean[][] occupied, int column, int row, int colSpan, int rowSpan) {
        for (int y = row; y < row + rowSpan; y++) {
            for (int x = column; x < column + colSpan; x++) occupied[y][x] = true;
        }
    }

    private static boolean isExpired(Card card) {
        if (card.expiresAt == null || card.expiresAt.isEmpty()) return false;
        try {
            return OffsetDateTime.parse(card.expiresAt).toInstant().isBefore(new Date().toInstant());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void drawFittedText(Canvas canvas, String value, float x, float baseline, float maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return;
        int count = paint.breakText(value, true, maxWidth, null);
        String visible = count >= value.length() ? value : value.substring(0, Math.max(1, count - 1)) + "…";
        canvas.drawText(visible, x, baseline, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float deltaX = event.getX() - downX;
        float deltaY = event.getY() - downY;
        boolean horizontalSwipe = Math.abs(deltaX) > 120f && Math.abs(deltaX) > Math.abs(deltaY) * 1.2f;
        float rightEdge = getWidth() * 0.86f;
        if (horizontalSwipe && downX >= rightEdge && deltaX < 0) {
            if (settingsAction != null) settingsAction.run();
            return true;
        }
        if (horizontalSwipe) {
            if (deltaX < 0) nextPage(); else previousPage();
            return true;
        }
        if (downY > getHeight() * 0.9f && downX < getWidth() * 0.25f) {
            if (homeAction != null) homeAction.run();
        } else if (downY > getHeight() * 0.9f && downX > getWidth() * 0.7f) {
            if (refreshAction != null) refreshAction.run();
        }
        return true;
    }

    private static final class Placement {
        final Card card;
        final String size;
        final int column;
        final int row;
        final int columnSpan;
        final int rowSpan;

        Placement(Card card, String size, int column, int row, int columnSpan, int rowSpan) {
            this.card = card;
            this.size = size;
            this.column = column;
            this.row = row;
            this.columnSpan = columnSpan;
            this.rowSpan = rowSpan;
        }
    }
}
