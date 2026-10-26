package com.liangmanman.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.BizException;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.User;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final WxMiniClient wxMiniClient;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final StoreMapper storeMapper;

    public AuthController(WxMiniClient wxMiniClient, TokenService tokenService,
                          UserMapper userMapper, StoreMapper storeMapper) {
        this.wxMiniClient = wxMiniClient;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.storeMapper = storeMapper;
    }

    public record LoginReq(@NotBlank(message = "缺少登录凭证") String code,
                           @Size(max = 64) String nickname,
                           @Size(max = 500) String avatar) {}

    public record LoginResp(String token, MeResp me) {}

    public record MeResp(Long id, String nickname, String avatar, String phone,
                         Long storeId, String storeStatus) {}

    public record UpdateMeReq(@Size(max = 64) String nickname,
                              @Size(max = 500) String avatar,
                              @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不对") String phone) {}

    /** 微信一键登录：首次登录自动注册 */
    @PostMapping("/api/auth/wx-login")
    @Transactional
    public ApiResult<LoginResp> wxLogin(@Valid @RequestBody LoginReq req) {
        WxMiniClient.WxSession session = wxMiniClient.code2Session(req.code());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenId, session.openId()));
        if (user == null) {
            user = new User();
            user.setOpenId(session.openId());
            user.setUnionId(session.unionId());
            user.setNickname(req.nickname() != null ? req.nickname() : "农友");
            user.setAvatar(req.avatar());
            userMapper.insert(user);
        } else if (session.unionId() != null && user.getUnionId() == null) {
            user.setUnionId(session.unionId());
            userMapper.updateById(user);
        }
        return ApiResult.ok(new LoginResp(tokenService.issue(user.getId()), toMe(user)));
    }

    @GetMapping("/api/me")
    public ApiResult<MeResp> me() {
        User user = userMapper.selectById(UserContext.require());
        if (user == null) {
            throw new BizException(BizException.UNAUTHORIZED, "请先登录");
        }
        return ApiResult.ok(toMe(user));
    }

    /**
     * 修改昵称、头像、手机号。
     * TODO 正式版手机号改为“手机号快速验证”组件返回的 code 换取，这里直接填写仅用于开发阶段。
     */
    @PutMapping("/api/me")
    public ApiResult<MeResp> updateMe(@Valid @RequestBody UpdateMeReq req) {
        User user = userMapper.selectById(UserContext.require());
        if (req.nickname() != null) user.setNickname(req.nickname());
        if (req.avatar() != null) user.setAvatar(req.avatar());
        if (req.phone() != null) user.setPhone(req.phone());
        userMapper.updateById(user);
        return ApiResult.ok(toMe(user));
    }

    private MeResp toMe(User user) {
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>().eq(Store::getOwnerId, user.getId()));
        return new MeResp(user.getId(), user.getNickname(), user.getAvatar(), user.getPhone(),
                store == null ? null : store.getId(), store == null ? null : store.getStatus());
    }
}
