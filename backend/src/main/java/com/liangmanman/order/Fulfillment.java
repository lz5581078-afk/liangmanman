package com.liangmanman.order;

/** 履约方式。DELIVERY 和 PICKUP 属于“本地配送”，EXPRESS 是“快递发货”。 */
public enum Fulfillment {
    DELIVERY("送货上门"),
    PICKUP("到店自提"),
    EXPRESS("快递发货");

    private final String label;

    Fulfillment(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isLocal() {
        return this != EXPRESS;
    }
}
