package com.liangmanman.auth;

/** 小程序登录：用 wx.login 拿到的 code 换 openid / unionid */
public interface WxMiniClient {

    WxSession code2Session(String code);

    record WxSession(String openId, String unionId) {}
}
