package com.zhixing.auth.controller;

import com.zhixing.auth.common.util.JwtTool;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWK 公钥接口，供网关验签
 */
@RestController
@RequestMapping("/jwks")
@RequiredArgsConstructor
public class JwkController {

    private final JwtTool jwtTool;

    @GetMapping
    public R<String> getJwk() {
        return R.ok(jwtTool.getPublicKeyBase64());
    }
}
