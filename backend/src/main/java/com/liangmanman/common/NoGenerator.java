package com.liangmanman.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号：前缀 + 时间(yyMMddHHmmss) + 4 位序号 + 2 位随机，总长 20，满足微信 out_trade_no ≤ 32 位。
 * 单实例部署足够；多实例时把序号换成 Redis INCR。
 */
public final class NoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private NoGenerator() {}

    public static String next(String prefix) {
        int seq = Math.floorMod(SEQ.incrementAndGet(), 10000);
        int rnd = ThreadLocalRandom.current().nextInt(100);
        return prefix + LocalDateTime.now().format(FMT) + String.format("%04d%02d", seq, rnd);
    }
}
