package com.liangmanman.service;

import com.liangmanman.common.BizException;
import com.liangmanman.entity.User;
import com.liangmanman.integration.WxApi;
import com.liangmanman.mapper.UserMapper;
import org.springframework.stereotype.Service;

/** 用户发布的文字过一遍微信内容安全检测（商品、店铺公告、评价、求购） */
@Service
public class ContentGuard {

    public static final int SCENE_PROFILE = 1;
    public static final int SCENE_COMMENT = 2;

    private final WxApi wx;
    private final UserMapper userMapper;

    public ContentGuard(WxApi wx, UserMapper userMapper) {
        this.wx = wx;
        this.userMapper = userMapper;
    }

    public void check(Long userId, int scene, String... texts) {
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (t != null && !t.isBlank()) sb.append(t).append('\n');
        }
        if (sb.isEmpty()) return;
        User u = userId == null ? null : userMapper.selectById(userId);
        if (!wx.textSafe(sb.toString(), u == null ? null : u.getOpenId(), scene)) {
            throw new BizException("内容里有不合适的词，请修改后再提交");
        }
    }
}
