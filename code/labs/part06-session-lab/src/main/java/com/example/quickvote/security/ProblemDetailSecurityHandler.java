package com.example.quickvote.security;

import java.io.IOException;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security が返す 401（認証なし）と 403（拒否）も、他のエラーと同じ ProblemDetail にそろえる。
 * Security のフィルタは DispatcherServlet より前で動くため、@RestControllerAdvice は通常届かない。
 * そこで HandlerExceptionResolver 経由で ProblemDetailAdvice に処理を委ねている。
 */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailSecurityHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        resolver.resolveException(request, response, null, new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        // CSRFトークンの不備は CsrfException（AccessDeniedException の子）として届く。
        ErrorCode code = accessDeniedException instanceof CsrfException ? ErrorCode.CSRF_REJECTED : ErrorCode.FORBIDDEN;
        resolver.resolveException(request, response, null, new ApiException(code));
    }
}
