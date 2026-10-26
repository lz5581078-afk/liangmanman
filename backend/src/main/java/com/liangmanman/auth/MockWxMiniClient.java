package com.liangmanman.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 开发环境：不调微信，同一个 code 对应同一个用户，方便模拟多个账号 */
@Component
@ConditionalOnProperty(name = "app.wx.mock", havingValue = "true")
public class MockWxMiniClient implements WxMiniClient {

    @Override
    public WxSession code2Session(String code) {
        return new WxSession("mock_" + code, null);
    }
}
