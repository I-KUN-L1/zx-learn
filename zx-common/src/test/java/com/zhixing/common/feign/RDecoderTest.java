package com.zhixing.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.exceptions.UnauthorizedException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RDecoder 单元测试：验证 R 信封解包与业务错误码转异常
 */
class RDecoderTest {

    private final RDecoder decoder = new RDecoder(new ObjectMapper());

    /** 模拟 Feign 返回体 */
    private Response response(String body) {
        Request request = Request.create(Request.HttpMethod.POST, "http://test/api",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    /** 测试用数据载体 */
    static class SampleDTO {
        public Long id;
        public String name;
    }

    @Test
    void unwrapSuccessEnvelope() throws Exception {
        String body = "{\"code\":200,\"msg\":\"OK\",\"data\":{\"id\":1,\"name\":\"张三\"},\"requestId\":\"r1\"}";
        Object result = decoder.decode(response(body), SampleDTO.class);
        assertInstanceOf(SampleDTO.class, result);
        assertEquals(1L, ((SampleDTO) result).id);
        assertEquals("张三", ((SampleDTO) result).name);
    }

    @Test
    void unwrapSuccessWithNullData() throws Exception {
        Object result = decoder.decode(
                response("{\"code\":200,\"msg\":\"OK\",\"data\":null,\"requestId\":\"r1\"}"), SampleDTO.class);
        assertNull(result);
    }

    @Test
    void errorEnvelopeWithoutDataFieldThrows401() throws Exception {
        // 网关/服务端错误体可能省略 data 字段：{"code":401,"msg":"..."}，必须识别为信封并抛异常
        UnauthorizedException e = assertThrows(UnauthorizedException.class, () ->
                decoder.decode(response("{\"code\":401,\"msg\":\"登录凭证无效\"}"), SampleDTO.class));
        assertEquals(401, e.getCode());
        assertEquals("登录凭证无效", e.getMessage());
    }

    @Test
    void errorEnvelopeWithNullDataThrows401() throws Exception {
        UnauthorizedException e = assertThrows(UnauthorizedException.class, () ->
                decoder.decode(response("{\"code\":401,\"msg\":\"用户名或密码错误\",\"data\":null}"), SampleDTO.class));
        assertEquals(401, e.getCode());
    }

    @Test
    void errorCodesMapToExceptionTypes() {
        assertThrows(BadRequestException.class, () ->
                decoder.decode(response("{\"code\":400,\"msg\":\"参数错误\"}"), SampleDTO.class));
        assertThrows(ForbiddenException.class, () ->
                decoder.decode(response("{\"code\":403,\"msg\":\"无权限\"}"), SampleDTO.class));
        BizIllegalException e = assertThrows(BizIllegalException.class, () ->
                decoder.decode(response("{\"code\":500,\"msg\":\"服务内部错误\"}"), SampleDTO.class));
        assertEquals(500, e.getCode());
    }

    @Test
    void unwrapGenericList() throws Exception {
        String body = "{\"code\":200,\"msg\":\"OK\",\"data\":[\"a\",\"b\"]}";
        Object result = decoder.decode(response(body),
                new ObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class));
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void rawNoWrapperBodyDecodesDirectly() throws Exception {
        // @NoWrapper 端点返回裸类型（如 adminExists 的 Boolean）
        assertEquals(Boolean.TRUE, decoder.decode(response("true"), Boolean.class));
        Object dto = decoder.decode(response("{\"id\":9,\"name\":\"裸返回\"}"), SampleDTO.class);
        assertEquals(9L, ((SampleDTO) dto).id);
    }

    @Test
    void declaredRTypeIsNotUnwrapped() throws Exception {
        String body = "{\"code\":200,\"msg\":\"OK\",\"data\":{\"id\":1}}";
        Object result = decoder.decode(response(body),
                new ObjectMapper().getTypeFactory().constructParametricType(R.class, SampleDTO.class));
        assertInstanceOf(R.class, result);
        assertEquals(200, ((R<?>) result).getCode());
    }

    @Test
    void voidTargetReturnsNull() throws Exception {
        assertNull(decoder.decode(response("{\"code\":200,\"msg\":\"OK\",\"data\":null}"), void.class));
    }

    @Test
    void stringTargetTakesTextualData() throws Exception {
        assertEquals("raw-jwk", decoder.decode(
                response("{\"code\":200,\"msg\":\"OK\",\"data\":\"raw-jwk\"}"), String.class));
    }

    @Test
    void emptyBodyReturnsNull() throws Exception {
        assertNull(decoder.decode(response(""), SampleDTO.class));
    }
}
