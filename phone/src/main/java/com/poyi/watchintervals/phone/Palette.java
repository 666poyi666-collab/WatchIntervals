package com.poyi.watchintervals.phone;

import android.graphics.Color;

/**
 * Fitness-dark design tokens for the phone app.
 *
 * <p>The companion used to wear a beige Material-ish coat of many inline colours; the visual
 * rebuild uses a platform-neutral training palette — true black behind #1C1C1E cards, white
 * figures, and original coral/mint/cyan accents doing the colour work.
 */
final class Palette {
    private Palette() {}

    static final int BG = PhoneColorSpec.BG;
    static final int NAV = PhoneColorSpec.NAV;
    static final int CARD = PhoneColorSpec.CARD;
    static final int CARD_HIGH = PhoneColorSpec.CARD_HIGH;
    static final int CARD_DEEP = PhoneColorSpec.CARD_DEEP;
    /** Floating functional layer: translucent, high-contrast, and separate from content cards. */
    static final int GLASS_TOP = Color.argb(244, 52, 52, 57);
    static final int GLASS_BOTTOM = Color.argb(235, 25, 25, 28);
    static final int GLASS_BORDER = Color.argb(72, 255, 255, 255);
    static final int GLASS_SELECTED = Color.argb(42, 255, 77, 103);
    static final int TEXT = PhoneColorSpec.TEXT;
    static final int TEXT_DIM = PhoneColorSpec.TEXT_DIM;
    static final int HINT = PhoneColorSpec.HINT;
    static final int INK = PhoneColorSpec.INK;

    /** Original training accents; they don't reuse Apple's protected Activity Rings palette. */
    static final int MOVE = PhoneColorSpec.MOVE;
    static final int EXERCISE = PhoneColorSpec.EXERCISE;
    static final int STAND = PhoneColorSpec.STAND;
    static final int YELLOW = PhoneColorSpec.YELLOW;
    static final int ORANGE = PhoneColorSpec.ORANGE;
    static final int RED = PhoneColorSpec.RED;
    static final int GREEN = PhoneColorSpec.GREEN;

    /** Deep tinted fills that carry a bright label of the matching accent. */
    static final int FILL_RUN = PhoneColorSpec.FILL_RUN;
    static final int FILL_WALK = PhoneColorSpec.FILL_WALK;
    static final int FILL_REST = PhoneColorSpec.FILL_REST;
    static final int FILL_DANGER = PhoneColorSpec.FILL_DANGER;
    static final int FILL_SELECTED = PhoneColorSpec.FILL_SELECTED;
}
