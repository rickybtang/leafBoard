package io.github.rickybtang.leafboard.storage;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.rickybtang.leafboard.SettingsStore;
import io.github.rickybtang.leafboard.model.Card;
import io.github.rickybtang.leafboard.model.ProducerCatalog;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CardRepository {
    private final Context context;
    private final File cardsDirectory;
    private final File catalogsDirectory;
    private final SharedPreferences syncPreferences;

    public CardRepository(Context context) {
        this.context = context.getApplicationContext();
        this.cardsDirectory = new File(context.getFilesDir(), "cards");
        this.catalogsDirectory = new File(context.getFilesDir(), "catalogs");
        this.syncPreferences = context.getSharedPreferences("leafboard.sync", Context.MODE_PRIVATE);
        cardsDirectory.mkdirs();
        catalogsDirectory.mkdirs();
    }

    public List<Card> loadCards() {
        List<Card> result = new ArrayList<>();
        File[] files = cardsDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;
        for (File file : files) {
            try {
                result.add(Card.parse(read(file)));
            } catch (Exception ignored) {
                // Invalid cache entries are ignored. A future valid sync can replace them.
            }
        }
        result.sort(Comparator.comparing(Card::ref));
        return result;
    }

    public List<Card> loadCatalogCards(List<String> producerIds) {
        List<Card> result = new ArrayList<>();
        for (String producerId : producerIds) {
            File catalogFile = new File(catalogsDirectory, producerId + ".json");
            if (!catalogFile.exists()) continue;
            try {
                ProducerCatalog catalog = ProducerCatalog.parse(read(catalogFile));
                if (!catalog.producerId.equals(producerId)) continue;
                for (ProducerCatalog.Entry entry : catalog.cards) {
                    try {
                        File cardFile = cardFile(producerId, entry.cardId);
                        if (!cardFile.exists()) continue;
                        byte[] bytes = readBytes(cardFile);
                        if (!sha256(bytes).equals(entry.sha256)) continue;
                        Card card = Card.parse(new String(bytes, StandardCharsets.UTF_8));
                        if (card.producerId.equals(producerId)
                                && card.cardId.equals(entry.cardId)
                                && card.revision == entry.revision) {
                            result.add(card);
                        }
                    } catch (Exception ignored) {
                        // A broken card must not hide other valid cards from the same producer.
                    }
                }
            } catch (Exception ignored) {
                // Keep the last valid layout visible for other configured producers.
            }
        }
        result.sort(Comparator.comparing(Card::ref));
        return result;
    }

    public int importInbox() {
        File inbox = new File(context.getExternalFilesDir(null), "inbox");
        inbox.mkdirs();
        File[] files = inbox.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return 0;
        int imported = 0;
        for (File file : files) {
            try {
                Card card = Card.parse(read(file));
                File destination = cardFile(card.producerId, card.cardId);
                Card existing = destination.exists() ? Card.parse(read(destination)) : null;
                if (existing == null || card.revision >= existing.revision) {
                    writeAtomic(destination, card.rawJson);
                    imported++;
                }
            } catch (Exception ignored) {
                // Keep the source file in place so the developer can inspect and replace it.
            }
        }
        return imported;
    }

    public SyncResult syncRemote(SettingsStore settings) {
        String username = settings.username();
        String password = settings.password();
        if (username.isEmpty() || password.isEmpty()) {
            return new SyncResult(0, "尚未配置 WebDAV");
        }
        if (settings.producerIds().isEmpty()) {
            return new SyncResult(0, "尚未配置数据来源 ID");
        }

        int changed = 0;
        List<String> failures = new ArrayList<>();
        for (String producerId : settings.producerIds()) {
            try {
                changed += syncProducer(settings.webDavUrl(), username, password, producerId);
            } catch (Exception error) {
                failures.add(producerId + ": " + safeMessage(error));
            }
        }
        if (!failures.isEmpty()) {
            return new SyncResult(changed, "部分同步失败 · " + String.join("；", failures));
        }
        return new SyncResult(changed, changed == 0 ? "数据无变化" : "已更新 " + changed + " 张卡片");
    }

    private int syncProducer(String rootUrl, String username, String password, String producerId) throws Exception {
        String base = rootUrl + "/v1/producers/" + producerId + "/";
        String etagKey = "etag." + producerId;
        HttpResult catalogResponse = get(base + "catalog.json", username, password, syncPreferences.getString(etagKey, null));
        if (catalogResponse.status == HttpURLConnection.HTTP_NOT_MODIFIED) return 0;
        requireSuccess(catalogResponse, "catalog");

        ProducerCatalog catalog = ProducerCatalog.parse(catalogResponse.body);
        if (!catalog.producerId.equals(producerId)) throw new JSONException("catalog producerId mismatch");
        File previousCatalogFile = new File(catalogsDirectory, producerId + ".json");
        if (previousCatalogFile.exists()) {
            try {
                ProducerCatalog previous = ProducerCatalog.parse(read(previousCatalogFile));
                if (catalog.revision < previous.revision) throw new JSONException("catalog revision decreased");
                if (catalog.revision == previous.revision) {
                    if (catalogResponse.etag != null) syncPreferences.edit().putString(etagKey, catalogResponse.etag).apply();
                    return 0;
                }
            } catch (JSONException error) {
                throw error;
            } catch (Exception ignored) {
                // A valid response may replace an unreadable local catalog cache.
            }
        }

        int changed = 0;
        Set<String> activeCardIds = new HashSet<>();
        for (ProducerCatalog.Entry entry : catalog.cards) {
            activeCardIds.add(entry.cardId);
            File destination = cardFile(producerId, entry.cardId);
            Card existing = null;
            if (destination.exists()) {
                byte[] existingBytes = readBytes(destination);
                existing = Card.parse(new String(existingBytes, StandardCharsets.UTF_8));
                if (sha256(existingBytes).equals(entry.sha256)) {
                    if (!existing.producerId.equals(producerId)
                            || !existing.cardId.equals(entry.cardId)
                            || existing.revision != entry.revision) {
                        throw new JSONException("cached card metadata mismatch: " + entry.cardId);
                    }
                    continue;
                }
            }

            HttpResult cardResponse = get(base + entry.path, username, password, null);
            requireSuccess(cardResponse, entry.path);
            byte[] bytes = cardResponse.body.getBytes(StandardCharsets.UTF_8);
            if (!sha256(bytes).equals(entry.sha256)) throw new IOException("card hash mismatch: " + entry.cardId);
            Card card = Card.parse(cardResponse.body);
            if (!card.producerId.equals(producerId) || !card.cardId.equals(entry.cardId)) {
                throw new JSONException("card identity mismatch: " + entry.cardId);
            }
            if (card.revision != entry.revision) throw new JSONException("card revision mismatch: " + entry.cardId);
            if (existing != null && card.revision <= existing.revision) {
                throw new JSONException("card revision did not increase: " + entry.cardId);
            }
            writeAtomic(destination, card.rawJson);
            changed++;
        }

        File[] cached = cardsDirectory.listFiles((dir, name) -> name.startsWith(producerId + "__") && name.endsWith(".json"));
        if (cached != null) {
            for (File file : cached) {
                String cardId = file.getName().substring((producerId + "__").length(), file.getName().length() - 5);
                if (!activeCardIds.contains(cardId) && file.delete()) changed++;
            }
        }

        writeAtomic(new File(catalogsDirectory, producerId + ".json"), catalogResponse.body);
        if (catalogResponse.etag != null) syncPreferences.edit().putString(etagKey, catalogResponse.etag).apply();
        return changed;
    }

    private HttpResult get(String url, String username, String password, String etag) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        String auth = username + ":" + password;
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        if (etag != null && !etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);
        int status = connection.getResponseCode();
        String body = "";
        if (status != HttpURLConnection.HTTP_NOT_MODIFIED) {
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream != null) body = new String(readAll(stream), StandardCharsets.UTF_8);
        }
        return new HttpResult(status, body, connection.getHeaderField("ETag"));
    }

    private static void requireSuccess(HttpResult result, String resource) throws IOException {
        if (result.status < 200 || result.status >= 300) {
            throw new IOException(resource + " HTTP " + result.status);
        }
    }

    private File cardFile(String producerId, String cardId) {
        return new File(cardsDirectory, producerId + "__" + cardId + ".json");
    }

    private static void writeAtomic(File destination, String value) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) throw new IOException("cannot replace " + destination.getName());
        if (!temporary.renameTo(destination)) throw new IOException("cannot commit " + destination.getName());
    }

    private static String read(File file) throws IOException {
        return new String(readBytes(file), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item));
        return result.toString();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static final class HttpResult {
        final int status;
        final String body;
        final String etag;

        HttpResult(int status, String body, String etag) {
            this.status = status;
            this.body = body;
            this.etag = etag;
        }
    }

    public static final class SyncResult {
        public final int changedCards;
        public final String message;

        public SyncResult(int changedCards, String message) {
            this.changedCards = changedCards;
            this.message = message;
        }
    }
}
