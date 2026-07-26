package com.poyi.watchintervals;

import java.util.Locale;

/**
 * Pure display formatters shared by every watch screen.
 *
 * <p>Kept free of Android imports so the JVM test suite covers them — the same reason
 * {@link SpeedFusion#formatPace} lives where it does. Before this class each activity carried its
 * own private copy, and they had already drifted: the phone app rolled to h:mm:ss past the hour
 * while every watch clock silently kept counting minutes ("75:32" an hour and a quarter into a
 * long run).
 */
final class Format {
    private Format() {}

    /** Duration clock: mm:ss, rolling to h:mm:ss past the hour exactly like the phone app. */
    static String duration(long millis) {
        long total = Math.max(0, millis / 1000);
        long hours = total / 3600, minutes = (total % 3600) / 60, seconds = total % 60;
        if (hours > 0) return String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    /** Distance figure: whole metres below 1 km, two-decimal kilometres from there on. */
    static String distance(double meters) {
        if (meters < 1000) return Math.round(meters) + " m";
        return String.format(Locale.CHINA, "%.2f km", meters / 1000d);
    }
}
