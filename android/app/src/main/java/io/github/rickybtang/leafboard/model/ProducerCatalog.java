package io.github.rickybtang.leafboard.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProducerCatalog {
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");
    private static final Pattern HASH = Pattern.compile("^[a-f0-9]{64}$");

    public final String producerId;
    public final long revision;
    public final String updatedAt;
    public final List<Entry> cards;

    private ProducerCatalog(String producerId, long revision, String updatedAt, List<Entry> cards) {
        this.producerId = producerId;
        this.revision = revision;
        this.updatedAt = updatedAt;
        this.cards = Collections.unmodifiableList(cards);
    }

    public static ProducerCatalog parse(String raw) throws JSONException {
        JSONObject root = new JSONObject(raw);
        require("1.0".equals(root.optString("schemaVersion")), "unsupported catalog schemaVersion");
        String producerId = root.getString("producerId");
        require(ID.matcher(producerId).matches(), "invalid producerId");
        long revision = root.getLong("revision");
        require(revision >= 0, "invalid catalog revision");
        JSONArray array = root.getJSONArray("cards");
        require(array.length() <= 100, "too many cards");
        List<Entry> entries = new ArrayList<>();
        Set<String> cardIds = new HashSet<>();
        for (int index = 0; index < array.length(); index++) {
            Entry entry = Entry.parse(array.getJSONObject(index));
            require(cardIds.add(entry.cardId), "duplicate cardId");
            entries.add(entry);
        }
        return new ProducerCatalog(producerId, revision, root.getString("updatedAt"), entries);
    }

    private static void require(boolean condition, String message) throws JSONException {
        if (!condition) throw new JSONException(message);
    }

    public static final class Entry {
        public final String cardId;
        public final String path;
        public final long revision;
        public final String sha256;

        private Entry(String cardId, String path, long revision, String sha256) {
            this.cardId = cardId;
            this.path = path;
            this.revision = revision;
            this.sha256 = sha256;
        }

        static Entry parse(JSONObject object) throws JSONException {
            String cardId = object.getString("cardId");
            String path = object.getString("path");
            String sha256 = object.getString("sha256");
            require(ID.matcher(cardId).matches(), "invalid cardId");
            require(path.equals("cards/" + cardId + ".json"), "invalid card path");
            require(HASH.matcher(sha256).matches(), "invalid sha256");
            long revision = object.getLong("revision");
            require(revision >= 0, "invalid card revision");
            return new Entry(cardId, path, revision, sha256);
        }
    }
}
