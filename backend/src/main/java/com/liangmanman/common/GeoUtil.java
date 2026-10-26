package com.liangmanman.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 距离计算（球面距离），以及按半径换算经纬度范围，用于“附近”查询的粗筛。 */
public final class GeoUtil {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtil() {}

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double distanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        return distanceKm(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    /** 保留 1 位小数，用于展示 */
    public static double round1(double km) {
        return BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 返回 [minLat, maxLat, minLng, maxLng]，覆盖以 (lat,lng) 为中心、radiusKm 为半径的圆 */
    public static double[] boundingBox(double lat, double lng, double radiusKm) {
        double dLat = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);
        double cos = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double dLng = Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * cos));
        return new double[]{lat - dLat, lat + dLat, lng - dLng, lng + dLng};
    }
}
