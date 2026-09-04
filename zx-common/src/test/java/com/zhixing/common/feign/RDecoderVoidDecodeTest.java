package com.zhixing.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.exceptions.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 回归测试：void 返回方法的 Feign 调用必须经过 Decoder。
 *
 * <p>背景事故：feign 对 void 方法默认不调用 Decoder（decodeVoid=false），
 * 服务端"HTTP 200 + R 包装体"约定下的 {@code R{code!=200}} 业务错误信封被静默吞掉，
 * 调用方误判成功（首次改密传错旧密码仍返回成功并删除凭据文件）。
 * 修复后 Builder 开启 {@code decodeVoid}，void 响应同样经过 {@link RDecoder} 转异常。
 */
class RDecoderVoidDecodeTest {

    private static HttpServer server;
    private static String baseUrl;

    /** 模拟 zx-api 中 void 返回的 Feign 接口（如 UserClient.changeBootstrapPassword） */
    interface VoidApi {
        @feign.RequestLine("PUT /bootstrap/password")
        void changePassword();

        @feign.RequestLine("PUT /ok")
        void changePasswordOk();
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bootstrap/password", exchange -> {
            byte[] body = "{\"code\":400,\"msg\":\"原密码错误\",\"data\":null,\"requestId\":\"r1\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"code\":200,\"msg\":\"OK\",\"data\":null,\"requestId\":\"r2\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private VoidApi buildApi() {
        // 与 FeignRDecoderAutoConfiguration 一致：decodeVoid + RDecoder 组合
        return Feign.builder()
                .decodeVoid()
                .decoder(new RDecoder(new ObjectMapper()))
                .target(VoidApi.class, baseUrl);
    }

    @Test
    void voidMethodWithBusinessErrorThrows() {
        VoidApi api = buildApi();
        // feign 将 Decoder 抛出的业务异常包装为 DecodeException（message/cause 保留），
        // 调用方（如 AdminBootstrapService）按约定解包 cause 还原业务异常
        feign.codec.DecodeException ex = assertThrows(feign.codec.DecodeException.class, api::changePassword);
        assert ex.getMessage() != null && ex.getMessage().contains("原密码错误") : ex.getMessage();
        assert ex.getCause() instanceof BadRequestException : ex.getCause();
    }

    @Test
    void voidMethodWithSuccessEnvelopeReturns() {
        VoidApi api = buildApi();
        assertDoesNotThrow(api::changePasswordOk);
    }
}
