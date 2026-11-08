package com.example.quickvote.security;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 暫定の「訪問者の特定」。コントローラーの引数 VisitorPrincipal を、リクエストヘッダーの値から作る。
 * <p>
 * この時点の保証: ヘッダーは誰でも書き換えられるので、他人になりすませる。認証ではない。
 * 第6部で、Cookie の訪問者トークン＋Spring Security（@AuthenticationPrincipal）に置き換える。
 * コントローラー側の引数の型（VisitorPrincipal）は変わらない。
 */
public class StandInVisitorArgumentResolver implements HandlerMethodArgumentResolver {

    private final String headerName;

    public StandInVisitorArgumentResolver(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return VisitorPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String value = webRequest.getHeader(headerName);
        try {
            long visitorId = Long.parseLong(value == null ? "" : value.strip());
            if (visitorId > 0) {
                return new VisitorPrincipal(visitorId);
            }
        } catch (NumberFormatException e) {
            // 下で 401 にする
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}
