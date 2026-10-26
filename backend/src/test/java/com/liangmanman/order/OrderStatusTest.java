package com.liangmanman.order;

import com.liangmanman.common.GeoUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void deliveryPath() {
        assertTrue(OrderStatus.PENDING_PAY.canGoTo(OrderStatus.PENDING_ACCEPT));
        assertTrue(OrderStatus.PENDING_ACCEPT.canGoTo(OrderStatus.ACCEPTED));
        assertTrue(OrderStatus.ACCEPTED.canGoTo(OrderStatus.DELIVERING));
        assertTrue(OrderStatus.DELIVERING.canGoTo(OrderStatus.DELIVERED));
        assertTrue(OrderStatus.DELIVERED.canGoTo(OrderStatus.COMPLETED));
    }

    @Test
    void cannotSkipOrGoBack() {
        assertFalse(OrderStatus.PENDING_ACCEPT.canGoTo(OrderStatus.DELIVERED));
        assertFalse(OrderStatus.DELIVERING.canGoTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.COMPLETED.canGoTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.CANCELLED.canGoTo(OrderStatus.PENDING_PAY));
        assertFalse(OrderStatus.SHIPPED.canGoTo(OrderStatus.CANCELLED));
    }

    @Test
    void distance() {
        // 郑州 -> 开封 约 63 公里
        double km = GeoUtil.distanceKm(34.7466, 113.6253, 34.7972, 114.3076);
        assertTrue(km > 60 && km < 66, "实际 " + km);
        assertEquals(0.0, GeoUtil.distanceKm(34, 113, 34, 113), 1e-9);
    }
}
