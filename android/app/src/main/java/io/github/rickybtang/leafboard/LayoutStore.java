package io.github.rickybtang.leafboard;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.rickybtang.leafboard.model.Card;

public final class LayoutStore {
    private final SharedPreferences preferences;

    public LayoutStore(Context context) {
        preferences = context.getSharedPreferences("leafboard.layout", Context.MODE_PRIVATE);
    }

    public boolean enabled(Card card) {
        return preferences.getBoolean("enabled." + card.ref(), true);
    }

    public void setEnabled(Card card, boolean enabled) {
        preferences.edit().putBoolean("enabled." + card.ref(), enabled).apply();
    }

    public String size(Card card) {
        String configured = preferences.getString("size." + card.ref(), card.preferredSize);
        if (configured != null && card.allowedSizes.contains(configured)) return configured;
        return card.allowedSizes.contains(card.preferredSize) ? card.preferredSize : card.allowedSizes.get(0);
    }

    public void setSize(Card card, String size) {
        if (card.allowedSizes.contains(size)) {
            preferences.edit().putString("size." + card.ref(), size).apply();
        }
    }

    public int order(Card card, int fallback) {
        return preferences.getInt("order." + card.ref(), fallback);
    }

    public void setOrder(Card card, int order) {
        preferences.edit().putInt("order." + card.ref(), Math.max(0, order)).apply();
    }
}
