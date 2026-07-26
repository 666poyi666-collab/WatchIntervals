package com.poyi.watchintervals.phone;

import android.graphics.Color;

/**
 * Fitness-dark design tokens for the phone app.
 *
 * <p>The companion used to wear a beige Material-ish coat of many inline colours; the visual
 * rebuild puts every surface on the Apple-fitness palette — true black behind #1C1C1E cards,
 * white figures, and the vivid move/exercise/stand accents doing the colour work.
 */
final class Palette {
    private Palette() {}

    static final int BG = Color.rgb(0, 0, 0);
    static final int NAV = Color.rgb(18, 18, 20);
    static final int CARD = Color.rgb(28, 28, 30);
    static final int CARD_HIGH = Color.rgb(44, 44, 46);
    static final int CARD_DEEP = Color.rgb(36, 36, 38);
    static final int TEXT = Color.rgb(255, 255, 255);
    static final int TEXT_DIM = Color.rgb(142, 142, 147);
    static final int HINT = Color.rgb(110, 110, 116);

    /** The activity-ring trio plus supporting brights. */
    static final int MOVE = Color.rgb(250, 17, 79);
    static final int EXERCISE = Color.rgb(146, 232, 42);
    static final int STAND = Color.rgb(0, 216, 255);
    static final int YELLOW = Color.rgb(255, 214, 10);
    static final int ORANGE = Color.rgb(255, 159, 10);
    static final int RED = Color.rgb(255, 69, 58);
    static final int GREEN = Color.rgb(48, 209, 88);

    /** Deep tinted fills that carry a bright label of the matching accent. */
    static final int FILL_RUN = Color.rgb(40, 58, 14);
    static final int FILL_WALK = Color.rgb(10, 48, 56);
    static final int FILL_REST = Color.rgb(62, 46, 10);
    static final int FILL_DANGER = Color.rgb(58, 24, 26);
    static final int FILL_SELECTED = Color.rgb(38, 58, 16);
}
