package com.poyi.watchintervals.phone;

import android.graphics.Color;

/** Light, data-first design tokens for the phone companion. */
final class Palette {
    private Palette() {}

    static final int BG = PhoneColorSpec.BG;
    static final int NAV = PhoneColorSpec.NAV;
    static final int CARD = PhoneColorSpec.CARD;
    static final int CARD_HIGH = PhoneColorSpec.CARD_HIGH;
    static final int CARD_DEEP = PhoneColorSpec.CARD_DEEP;
    static final int BORDER = PhoneColorSpec.BORDER;
    /** Floating functional layer: translucent daylight glass above the content cards. */
    static final int GLASS_TOP = Color.argb(250, 255, 255, 255);
    static final int GLASS_BOTTOM = Color.argb(242, 245, 248, 251);
    static final int GLASS_BORDER = Color.argb(210, 214, 221, 229);
    static final int GLASS_SELECTED = Color.argb(255, 252, 231, 236);
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

    static final int SLEEP_DEEP = PhoneColorSpec.SLEEP_DEEP;
    static final int SLEEP_LIGHT = PhoneColorSpec.SLEEP_LIGHT;
    static final int SLEEP_REM = PhoneColorSpec.SLEEP_REM;
    static final int SLEEP_AWAKE = PhoneColorSpec.SLEEP_AWAKE;
}
