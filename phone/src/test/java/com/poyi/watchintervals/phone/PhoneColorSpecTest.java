package com.poyi.watchintervals.phone;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class PhoneColorSpecTest {
    @Test public void textAndControlsMeetBodyContrastThreshold() {
        assertContrast("primary text on card", PhoneColorSpec.TEXT, PhoneColorSpec.CARD, 4.5);
        assertContrast("secondary text on raised card", PhoneColorSpec.TEXT_DIM,
                PhoneColorSpec.CARD_HIGH, 4.5);
        assertContrast("input hint on raised card", PhoneColorSpec.HINT,
                PhoneColorSpec.CARD_HIGH, 4.5);
        assertContrast("ink on mint action", PhoneColorSpec.INK, PhoneColorSpec.EXERCISE, 4.5);
        assertContrast("ink on coral action", PhoneColorSpec.INK, PhoneColorSpec.MOVE, 4.5);
        assertContrast("run label", PhoneColorSpec.EXERCISE, PhoneColorSpec.FILL_RUN, 4.5);
        assertContrast("walk label", PhoneColorSpec.STAND, PhoneColorSpec.FILL_WALK, 4.5);
        assertContrast("rest label", PhoneColorSpec.YELLOW, PhoneColorSpec.FILL_REST, 4.5);
        assertContrast("danger label", PhoneColorSpec.RED, PhoneColorSpec.FILL_DANGER, 4.5);
    }

    private static void assertContrast(String message, int first, int second, double minimum) {
        double ratio = contrast(first, second);
        assertTrue(message + " contrast was " + ratio, ratio >= minimum);
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 0xff)
                + 0.7152 * channel((color >> 8) & 0xff)
                + 0.0722 * channel(color & 0xff);
    }

    private static double channel(int value) {
        double normalized = value / 255d;
        return normalized <= 0.04045 ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
