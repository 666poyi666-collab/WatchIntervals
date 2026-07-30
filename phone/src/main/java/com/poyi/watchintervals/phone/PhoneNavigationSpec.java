package com.poyi.watchintervals.phone;

/** Stable order and accessibility contract for the phone's four top-level destinations. */
final class PhoneNavigationSpec {
    static final Item[] ITEMS = new Item[]{
            new Item(PhoneSymbol.PLAN, "计划", "训练计划"),
            new Item(PhoneSymbol.WORKOUT, "训练", "训练控制"),
            new Item(PhoneSymbol.HISTORY, "历史", "训练历史"),
            new Item(PhoneSymbol.SLEEP, "睡眠", "睡眠记录")
    };

    private PhoneNavigationSpec() {}

    static final class Item {
        final PhoneSymbol symbol;
        final String label;
        final String accessibilityLabel;

        Item(PhoneSymbol symbol, String label, String accessibilityLabel) {
            this.symbol = symbol;
            this.label = label;
            this.accessibilityLabel = accessibilityLabel;
        }
    }
}
