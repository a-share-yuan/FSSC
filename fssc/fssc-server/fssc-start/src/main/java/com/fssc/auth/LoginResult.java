// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.auth;

import com.fssc.framework.security.LoginUser;

/** 登录响应：Token + 登录用户信息 */
public class LoginResult {

    private String token;
    private LoginUser user;

    public LoginResult() {}

    public LoginResult(String token, LoginUser user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LoginUser getUser() { return user; }
    public void setUser(LoginUser user) { this.user = user; }
}
