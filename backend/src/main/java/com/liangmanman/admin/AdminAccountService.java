package com.liangmanman.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liangmanman.auth.TokenService;
import com.liangmanman.common.BizException;
import com.liangmanman.common.PageResult;
import com.liangmanman.config.AppProperties;
import com.liangmanman.entity.Admin;
import com.liangmanman.entity.AdminLog;
import com.liangmanman.mapper.AdminLogMapper;
import com.liangmanman.mapper.AdminMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理员账号、登录、操作日志 */
@Slf4j
@Service
public class AdminAccountService {

    private final AdminMapper adminMapper;
    private final AdminLogMapper logMapper;
    private final TokenService tokenService;
    private final AppProperties.Admin cfg;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminAccountService(AdminMapper adminMapper, AdminLogMapper logMapper, TokenService tokenService,
                               AppProperties props) {
        this.adminMapper = adminMapper;
        this.logMapper = logMapper;
        this.tokenService = tokenService;
        this.cfg = props.admin();
    }

    /** 首次启动没有管理员时，按配置创建超级管理员（未配置初始密码则跳过） */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        try {
            if (adminMapper.selectCount(null) > 0) return;
            if (cfg.initPassword() == null || cfg.initPassword().isBlank()) {
                log.warn("还没有管理员账号。设置环境变量 ADMIN_INIT_PASSWORD 后重启，会自动创建管理员 {}", cfg.initUsername());
                return;
            }
            Admin a = new Admin();
            a.setUsername(cfg.initUsername() == null ? "admin" : cfg.initUsername());
            a.setPasswordHash(encoder.encode(cfg.initPassword()));
            a.setName("超级管理员");
            a.setRole(Admin.ADMIN);
            a.setEnabled(true);
            adminMapper.insert(a);
            log.info("已创建管理员 {}，请登录后修改密码", a.getUsername());
        } catch (Exception e) {
            log.warn("初始化管理员失败（数据库未建表？）：{}", e.getMessage());
        }
    }

    public record LoginResp(String token, Admin admin) {}

    public LoginResp login(String username, String password) {
        Admin a = adminMapper.selectOne(new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, username));
        if (a == null || !encoder.matches(password, a.getPasswordHash())) {
            throw new BizException("账号或密码不对");
        }
        if (!Boolean.TRUE.equals(a.getEnabled())) {
            throw new BizException("账号已停用");
        }
        log(a, "登录", null, null);
        return new LoginResp(tokenService.issueAdmin(a.getId()), a);
    }

    public void changePassword(Admin me, String oldPassword, String newPassword) {
        Admin a = adminMapper.selectById(me.getId());
        if (!encoder.matches(oldPassword, a.getPasswordHash())) {
            throw new BizException("原密码不对");
        }
        checkStrength(newPassword);
        a.setPasswordHash(encoder.encode(newPassword));
        adminMapper.updateById(a);
        log(me, "修改密码", null, null);
    }

    public List<Admin> list() {
        return adminMapper.selectList(new LambdaQueryWrapper<Admin>().orderByAsc(Admin::getId));
    }

    public Admin create(Admin me, String username, String password, String name, String role) {
        if (!Admin.ADMIN.equals(role) && !Admin.OPERATOR.equals(role)) {
            throw new BizException("角色不对");
        }
        if (adminMapper.selectCount(new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, username)) > 0) {
            throw new BizException("账号已存在");
        }
        checkStrength(password);
        Admin a = new Admin();
        a.setUsername(username);
        a.setPasswordHash(encoder.encode(password));
        a.setName(name);
        a.setRole(role);
        a.setEnabled(true);
        adminMapper.insert(a);
        log(me, "新增管理员", username, role);
        return a;
    }

    public void resetPassword(Admin me, Long id, String password) {
        Admin a = require(id);
        checkStrength(password);
        a.setPasswordHash(encoder.encode(password));
        adminMapper.updateById(a);
        log(me, "重置密码", a.getUsername(), null);
    }

    public Admin toggle(Admin me, Long id) {
        Admin a = require(id);
        if (a.getId().equals(me.getId())) {
            throw new BizException("不能停用自己");
        }
        a.setEnabled(!Boolean.TRUE.equals(a.getEnabled()));
        adminMapper.updateById(a);
        log(me, a.getEnabled() ? "启用管理员" : "停用管理员", a.getUsername(), null);
        return a;
    }

    private Admin require(Long id) {
        Admin a = adminMapper.selectById(id);
        if (a == null) throw BizException.notFound("管理员");
        return a;
    }

    private static void checkStrength(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BizException("密码至少 8 位，且包含字母和数字");
        }
    }

    // ---------- 操作日志 ----------

    public void log(Admin admin, String action, String target, String detail) {
        AdminLog l = new AdminLog();
        l.setAdminId(admin.getId());
        l.setAdminName(admin.getName());
        l.setAction(action);
        l.setTarget(target == null ? null : (target.length() > 64 ? target.substring(0, 64) : target));
        l.setDetail(detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail));
        logMapper.insert(l);
    }

    public PageResult<AdminLog> logs(Integer page, Integer size) {
        Page<AdminLog> p = logMapper.selectPage(PageResult.page(page, size),
                new LambdaQueryWrapper<AdminLog>().orderByDesc(AdminLog::getId));
        return PageResult.of(p);
    }
}
