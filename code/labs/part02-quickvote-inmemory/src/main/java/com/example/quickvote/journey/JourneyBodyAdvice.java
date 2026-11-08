package com.example.quickvote.journey;

import java.lang.reflect.Type;

import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * JSON と Java オブジェクトの変換の「前後」に差し込めるフック。変換そのものは Jackson が行う。
 * ここでは変換が終わった時点で1行ずつ出すだけで、中身（タイトルなどの入力値）は出さない。
 */
@ControllerAdvice
@Profile("journey")
public class JourneyBodyAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        Journey.step("Jackson 読む", "JSON → " + body.getClass().getSimpleName() + "（この後 @Valid の検問）");
        return body;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        Journey.step("Jackson 書く", (body == null ? "null" : body.getClass().getSimpleName()) + " → JSON");
        return body;
    }
}
