package com.liangmanman;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@MapperScan("com.liangmanman.mapper")
@ConfigurationPropertiesScan
@EnableScheduling
public class LiangmanmanApplication {
    public static void main(String[] args) {
        // 订单时间、超时判断、“今天送”都按北京时间，与服务器所在时区无关
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(LiangmanmanApplication.class, args);
    }
}
