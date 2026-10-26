package com.liangmanman.order;

public enum PayMethod {
    ONLINE("在线支付"),
    COD("货到付款");

    private final String label;

    PayMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
