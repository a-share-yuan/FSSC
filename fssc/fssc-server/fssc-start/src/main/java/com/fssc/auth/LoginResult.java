package com.fssc.auth;

import com.fssc.framework.security.LoginUser;

/**
 * 登录响应：Token + 登录用户信息 + 租户装配档。
 *
 * <p>opMode 放在响应顶层而不是 LoginUser 里：LoginUser 会被序列化进 JWT claims，
 * 装配档属于「登录时的一次性分流判据」，不该污染 Token，也不该在装配完成后还带着旧值。
 * 平台登录不带该字段（null）。</p>
 */
public class LoginResult {

    private String token;
    private LoginUser user;
    /** EXPENSE / CONTROL / SHARED；null 表示企业尚未装配，前端跳选档引导页 */
    private String opMode;

    public LoginResult() {}

    public LoginResult(String token, LoginUser user) {
        this.token = token;
        this.user = user;
    }

    public LoginResult(String token, LoginUser user, String opMode) {
        this.token = token;
        this.user = user;
        this.opMode = opMode;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LoginUser getUser() { return user; }
    public void setUser(LoginUser user) { this.user = user; }

    public String getOpMode() { return opMode; }
    public void setOpMode(String opMode) { this.opMode = opMode; }
}
