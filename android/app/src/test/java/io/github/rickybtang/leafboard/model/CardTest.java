package io.github.rickybtang.leafboard.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.json.JSONException;

public class CardTest {
    @Test
    public void formatsDurationFromSeconds() {
        assertEquals("0秒", Card.Field.formatDuration(0));
        assertEquals("1分30秒", Card.Field.formatDuration(90));
        assertEquals("2时", Card.Field.formatDuration(7200));
    }

    @Test
    public void parsesNumericFirstValueInLargeDualValueField() throws Exception {
        Card card = Card.parse("""
                {
                  "schemaVersion":"1.0",
                  "producerId":"test",
                  "cardId":"duration-summary",
                  "revision":1,
                  "type":"metric",
                  "updatedAt":"2026-08-31T08:00:00+08:00",
                  "content":{"title":"时长统计","fields":[
                    {"key":"today-count","label":"今日数量","value":1200,"format":"number","unit":"次","role":"primary","minSize":"small"},
                    {"key":"recent-7-days","label":"近7天","value":7200,"format":"duration","unit":"s","role":"detail","minSize":"large","secondary":{"value":24000,"format":"number","unit":"次"}}
                  ]},
                  "presentation":{"icon":"text","preferredSize":"large","allowedSizes":["small","medium","large"],"status":"normal"}
                }
                """);

        Card.Field aggregate = card.fields.get(1);
        assertEquals("2时", aggregate.displayValue());
        assertEquals("24K次", aggregate.secondary.displayValue());
    }

    @Test(expected = JSONException.class)
    public void rejectsPreformattedDurationText() throws Exception {
        Card.parse("""
                {
                  "schemaVersion":"1.0",
                  "producerId":"test",
                  "cardId":"invalid-duration",
                  "revision":1,
                  "type":"metric",
                  "updatedAt":"2026-08-31T08:00:00+08:00",
                  "content":{"title":"时长统计","fields":[
                    {"key":"count","label":"今日数量","value":100,"format":"number","unit":"次","role":"primary","minSize":"small"},
                    {"key":"recent-7-days","label":"近7天","value":"2时","format":"duration","unit":"s","role":"detail","minSize":"large","secondary":{"value":24000,"format":"number","unit":"次"}}
                  ]},
                  "presentation":{"icon":"text","preferredSize":"large","allowedSizes":["large"],"status":"normal"}
                }
                """);
    }

    @Test(expected = JSONException.class)
    public void rejectsDuplicateCatalogCardIds() throws Exception {
        ProducerCatalog.parse("""
                {
                  "schemaVersion":"1.0",
                  "producerId":"test",
                  "revision":2,
                  "updatedAt":"2026-09-01T12:00:00+08:00",
                  "cards":[
                    {"cardId":"usage","path":"cards/usage.json","revision":1,"sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"},
                    {"cardId":"usage","path":"cards/usage.json","revision":1,"sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
                  ]
                }
                """);
    }

    @Test(expected = JSONException.class)
    public void rejectsNegativeCatalogCardRevision() throws Exception {
        ProducerCatalog.parse("""
                {
                  "schemaVersion":"1.0",
                  "producerId":"test",
                  "revision":2,
                  "updatedAt":"2026-09-01T12:00:00+08:00",
                  "cards":[
                    {"cardId":"usage","path":"cards/usage.json","revision":-1,"sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
                  ]
                }
                """);
    }
}
