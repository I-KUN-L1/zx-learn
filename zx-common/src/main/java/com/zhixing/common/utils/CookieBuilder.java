package com.zhixing.common.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookie 构建工具
 */
public class CookieBuilder {

    private final String name;
    private String value;
    private String domain;
    private String path = "/";
    private int maxAge = -1;
    private boolean httpOnly = true;
    private boolean secure = false;
    private String sameSite;

    private CookieBuilder(String name) {
        this.name = name;
    }

    public static CookieBuilder newBuilder(String name) {
        return new CookieBuilder(name);
    }

    public CookieBuilder value(String value) {
        this.value = value;
        return this;
    }

    public CookieBuilder domain(String domain) {
        this.domain = domain;
        return this;
    }

    public CookieBuilder path(String path) {
        this.path = path;
        return this;
    }

    public CookieBuilder maxAge(int maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public CookieBuilder httpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public CookieBuilder secure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public CookieBuilder sameSite(String sameSite) {
        this.sameSite = sameSite;
        return this;
    }

    public Cookie build() {
        Cookie cookie = new Cookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        return cookie;
    }

    public void write(HttpServletResponse response) {
        Cookie cookie = build();
        if (StringUtils.isNotBlank(sameSite)) {
            response.setHeader("Set-Cookie", String.format(
                    "%s=%s; Path=%s; Max-Age=%d; HttpOnly; SameSite=%s",
                    name, value, path, maxAge, sameSite));
        } else {
            response.addCookie(cookie);
        }
    }
}
