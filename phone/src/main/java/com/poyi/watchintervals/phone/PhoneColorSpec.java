package com.poyi.watchintervals.phone;

/** Platform-independent ARGB design tokens so contrast can be verified in local JVM tests. */
final class PhoneColorSpec {
    // The phone is a reading and planning surface, so daylight legibility is the default.
    static final int BG = 0xfff5f7fa;
    static final int NAV = 0xffffffff;
    static final int CARD = 0xffffffff;
    static final int CARD_HIGH = 0xffeef2f6;
    static final int CARD_DEEP = 0xffe7ecf2;
    static final int BORDER = 0xffdce2e9;
    static final int TEXT = 0xff17212b;
    static final int TEXT_DIM = 0xff516170;
    static final int HINT = 0xff5e6d7b;
    static final int INK = 0xffffffff;

    static final int MOVE = 0xffc72c4d;
    static final int EXERCISE = 0xff2f7d32;
    static final int STAND = 0xff006d86;
    static final int YELLOW = 0xff735400;
    static final int ORANGE = 0xffa9570c;
    static final int RED = 0xffb3263a;
    static final int GREEN = 0xff1f7a43;

    static final int FILL_RUN = 0xffe8f4e6;
    static final int FILL_WALK = 0xffe2f2f5;
    static final int FILL_REST = 0xfff7efd8;
    static final int FILL_DANGER = 0xfff9e7e9;
    static final int FILL_SELECTED = 0xfffce7ec;

    static final int SLEEP_DEEP = 0xff334e9d;
    static final int SLEEP_LIGHT = 0xff4f8fcf;
    static final int SLEEP_REM = 0xff7650a8;
    static final int SLEEP_AWAKE = 0xffb46516;

    private PhoneColorSpec() {}
}
