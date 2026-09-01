package io.github.rickybtang.leafboard.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Card {
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter COMPACT_LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    public final String producerId;
    public final String cardId;
    public final long revision;
    public final String type;
    public final String updatedAt;
    public final String expiresAt;
    public final String title;
    public final String state;
    public final List<Field> fields;
    public final List<ListItem> items;
    public final String preferredSize;
    public final List<String> allowedSizes;
    public final String status;
    public final String rawJson;

    private Card(
            String producerId,
            String cardId,
            long revision,
            String type,
            String updatedAt,
            String expiresAt,
            String title,
            String state,
            List<Field> fields,
            List<ListItem> items,
            String preferredSize,
            List<String> allowedSizes,
            String status,
            String rawJson
    ) {
        this.producerId = producerId;
        this.cardId = cardId;
        this.revision = revision;
        this.type = type;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
        this.title = title;
        this.state = state;
        this.fields = Collections.unmodifiableList(fields);
        this.items = Collections.unmodifiableList(items);
        this.preferredSize = preferredSize;
        this.allowedSizes = Collections.unmodifiableList(allowedSizes);
        this.status = status;
        this.rawJson = rawJson;
    }

    public static Card parse(String raw) throws JSONException {
        JSONObject root = new JSONObject(raw);
        require("1.0".equals(root.optString("schemaVersion")), "unsupported schemaVersion");

        String producerId = root.getString("producerId");
        String cardId = root.getString("cardId");
        require(ID.matcher(producerId).matches(), "invalid producerId");
        require(ID.matcher(cardId).matches(), "invalid cardId");

        long revision = root.getLong("revision");
        require(revision >= 0, "revision must be non-negative");

        String type = root.getString("type");
        require(type.equals("metric") || type.equals("list") || type.equals("status"), "unsupported type");

        String updatedAt = root.getString("updatedAt");
        String expiresAt = root.optString("expiresAt", null);
        JSONObject content = root.getJSONObject("content");
        String title = content.getString("title");
        require(!title.isEmpty() && title.length() <= 24, "invalid title");

        List<Field> fields = new ArrayList<>();
        List<ListItem> items = new ArrayList<>();
        String state = null;

        if (type.equals("list")) {
            JSONArray array = content.getJSONArray("items");
            require(array.length() <= 50, "too many list items");
            for (int index = 0; index < array.length(); index++) {
                items.add(ListItem.parse(array.getJSONObject(index)));
            }
        } else {
            if (type.equals("status")) {
                state = content.getString("state");
            }
            JSONArray array = content.getJSONArray("fields");
            require(array.length() >= 1 && array.length() <= 8, "invalid field count");
            for (int index = 0; index < array.length(); index++) {
                fields.add(Field.parse(array.getJSONObject(index)));
            }
            int primaryCount = 0;
            for (Field field : fields) {
                if ("primary".equals(field.role)) primaryCount++;
            }
            require(primaryCount == 1, "metric/status requires exactly one primary field");
        }

        JSONObject presentation = root.getJSONObject("presentation");
        String preferredSize = presentation.getString("preferredSize");
        require(sizeRank(preferredSize) >= 0, "invalid preferredSize");
        JSONArray sizes = presentation.getJSONArray("allowedSizes");
        require(sizes.length() > 0, "allowedSizes cannot be empty");
        List<String> allowedSizes = new ArrayList<>();
        for (int index = 0; index < sizes.length(); index++) {
            String size = sizes.getString(index);
            require(sizeRank(size) >= 0, "invalid allowed size");
            if (!allowedSizes.contains(size)) {
                allowedSizes.add(size);
            }
        }

        return new Card(
                producerId,
                cardId,
                revision,
                type,
                updatedAt,
                expiresAt,
                title,
                state,
                fields,
                items,
                preferredSize,
                allowedSizes,
                presentation.getString("status"),
                root.toString(2)
        );
    }

    public String ref() {
        return producerId + "/" + cardId;
    }

    public List<Field> visibleFields(String size) {
        int rank = sizeRank(size);
        List<Field> result = new ArrayList<>();
        for (Field field : fields) {
            if (sizeRank(field.minSize) <= rank) {
                result.add(field);
            }
        }
        int limit = rank == 0 ? 4 : rank == 1 ? 4 : 7;
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    public static int sizeRank(String size) {
        if ("small".equals(size)) return 0;
        if ("medium".equals(size)) return 1;
        if ("large".equals(size)) return 2;
        return -1;
    }

    private static void require(boolean condition, String message) throws JSONException {
        if (!condition) throw new JSONException(message);
    }

    public static final class Field {
        public final String key;
        public final String label;
        public final Object value;
        public final String format;
        public final String unit;
        public final String role;
        public final String minSize;
        public final Secondary secondary;

        private Field(String key, String label, Object value, String format, String unit, String role, String minSize, Secondary secondary) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.format = format;
            this.unit = unit;
            this.role = role;
            this.minSize = minSize;
            this.secondary = secondary;
        }

        static Field parse(JSONObject object) throws JSONException {
            String key = object.getString("key");
            String label = object.getString("label");
            require(ID.matcher(key).matches(), "invalid field key");
            require(!label.isEmpty() && label.length() <= 24, "invalid field label");
            Object value = object.isNull("value") ? null : object.get("value");
            String format = object.getString("format");
            String unit = object.optString("unit", "");
            String role = object.getString("role");
            String minSize = object.getString("minSize");
            Secondary secondary = object.has("secondary") ? Secondary.parse(object.getJSONObject("secondary")) : null;
            require(format.equals("text") || format.equals("number") || format.equals("percent")
                    || format.equals("money") || format.equals("datetime") || format.equals("duration")
                    || format.equals("boolean"), "invalid field format");
            require(unit.length() <= 12, "field unit too long");
            require(!"money".equals(format) || unit.matches("^[A-Z]{3}$"), "invalid money unit");
            if ("primary".equals(role)) {
                require(codePointCount(label) <= 8, "primary label too long");
                if ("text".equals(format)) {
                    require(value instanceof String && !((String) value).isEmpty() && codePointCount((String) value) <= 12, "invalid text primary value");
                }
            }
            if (secondary != null) {
                require("detail".equals(role) && "large".equals(minSize), "secondary requires large detail field");
                require(codePointCount(label) <= 8, "secondary field label too long");
                require(format.equals("text") || format.equals("number") || format.equals("percent")
                        || format.equals("money") || format.equals("duration"), "invalid first secondary field format");
                if ("text".equals(format)) {
                    require(value instanceof String && !((String) value).isEmpty()
                            && codePointCount((String) value) <= 12, "invalid secondary field text value");
                } else {
                    require(value instanceof Number, "secondary field first numeric value must be a number");
                }
            }
            if ("duration".equals(format)) {
                require(value instanceof Number && ((Number) value).doubleValue() >= 0, "duration must be non-negative seconds");
                require("s".equals(unit), "duration unit must be s");
            }
            return new Field(
                    key,
                    label,
                    value,
                    format,
                    unit,
                    role,
                    minSize,
                    secondary
            );
        }

        private static int codePointCount(String value) {
            return value.codePointCount(0, value.length());
        }

        public String displayValue() {
            if (value == null) return "—";
            if ("percent".equals(format) && value instanceof Number) {
                return String.format(Locale.US, "%.0f%%", ((Number) value).doubleValue());
            }
            if ("number".equals(format) && value instanceof Number
                    && ("token".equalsIgnoreCase(unit) || "tokens".equalsIgnoreCase(unit))) {
                return Secondary.compactNumber(((Number) value).doubleValue()) + " tok";
            }
            if ("duration".equals(format) && value instanceof Number) {
                return formatDuration(((Number) value).doubleValue());
            }
            if ("boolean".equals(format) && value instanceof Boolean) {
                return (Boolean) value ? "是" : "否";
            }
            String text = String.valueOf(value);
            if ("datetime".equals(format)) {
                try {
                    text = LOCAL_DATE_TIME.format(
                            OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault())
                    );
                } catch (RuntimeException ignored) {
                    text = text.replace('T', ' ');
                    int plus = Math.max(text.indexOf('+', 16), text.indexOf('Z', 16));
                    if (plus > 0) text = text.substring(0, plus);
                    if (text.length() > 16) text = text.substring(0, 16);
                }
            }
            if ("money".equals(format) && "CNY".equals(unit)) return text + "元";
            if ("money".equals(format) && "USD".equals(unit) && value instanceof Number) {
                return NumberFormat.getCurrencyInstance(Locale.US).format(((Number) value).doubleValue());
            }
            return unit.isEmpty() ? text : text + unit;
        }

        static String formatDuration(double rawSeconds) {
            long seconds = Math.max(0L, (long) Math.floor(rawSeconds));
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            long remainingSeconds = seconds % 60L;
            if (hours > 0L) {
                return minutes > 0L ? hours + "时" + minutes + "分" : hours + "时";
            }
            if (minutes > 0L) {
                return remainingSeconds > 0L ? minutes + "分" + remainingSeconds + "秒" : minutes + "分";
            }
            return remainingSeconds + "秒";
        }

        public String compactDisplayValue() {
            if (!"datetime".equals(format) || value == null) return displayValue();
            try {
                return COMPACT_LOCAL_DATE_TIME.format(
                        OffsetDateTime.parse(String.valueOf(value)).atZoneSameInstant(ZoneId.systemDefault())
                );
            } catch (RuntimeException ignored) {
                String text = displayValue();
                return text.length() > 5 ? text.substring(5) : text;
            }
        }

        public static final class Secondary {
            public final Number value;
            public final String format;
            public final String unit;

            private Secondary(Number value, String format, String unit) {
                this.value = value;
                this.format = format;
                this.unit = unit;
            }

            static Secondary parse(JSONObject object) throws JSONException {
                Object rawValue = object.get("value");
                require(rawValue instanceof Number, "secondary value must be number");
                String format = object.getString("format");
                require(format.equals("number") || format.equals("percent") || format.equals("money") || format.equals("duration"), "invalid secondary format");
                String unit = object.optString("unit", "");
                require(unit.length() <= 12, "secondary unit too long");
                require(!"money".equals(format) || unit.matches("^[A-Z]{3}$"), "invalid secondary money unit");
                require(!"duration".equals(format) || (((Number) rawValue).doubleValue() >= 0 && "s".equals(unit)), "invalid secondary duration");
                return new Secondary((Number) rawValue, format, unit);
            }

            public String displayValue() {
                double number = value.doubleValue();
                if ("percent".equals(format)) return String.format(Locale.US, "%.0f%%", number);
                if ("money".equals(format) && "CNY".equals(unit)) {
                    return compactNumber(number) + "元";
                }
                if ("duration".equals(format)) return formatDuration(number);
                String suffix = "token".equalsIgnoreCase(unit) || "tokens".equalsIgnoreCase(unit) ? " tok" : unit;
                return compactNumber(number) + suffix;
            }

            static String compactNumber(double number) {
                double absolute = Math.abs(number);
                if (absolute >= 1_000_000_000d) return trim(String.format(Locale.US, "%.1f", number / 1_000_000_000d)) + "B";
                if (absolute >= 1_000_000d) return trim(String.format(Locale.US, "%.1f", number / 1_000_000d)) + "M";
                if (absolute >= 1_000d) return trim(String.format(Locale.US, "%.1f", number / 1_000d)) + "K";
                return trim(String.format(Locale.US, "%.1f", number));
            }

            private static String trim(String value) {
                return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
            }
        }
    }

    public static final class ListItem {
        public final String id;
        public final String text;
        public final boolean checked;
        public final String dueAt;
        public final int priority;

        private ListItem(String id, String text, boolean checked, String dueAt, int priority) {
            this.id = id;
            this.text = text;
            this.checked = checked;
            this.dueAt = dueAt;
            this.priority = priority;
        }

        static ListItem parse(JSONObject object) throws JSONException {
            String id = object.getString("id");
            String text = object.getString("text");
            require(ID.matcher(id).matches(), "invalid list item id");
            require(!text.isEmpty() && text.length() <= 80, "invalid list item text");
            return new ListItem(
                    id,
                    text,
                    object.optBoolean("checked", false),
                    object.optString("dueAt", null),
                    object.optInt("priority", 4)
            );
        }
    }
}
