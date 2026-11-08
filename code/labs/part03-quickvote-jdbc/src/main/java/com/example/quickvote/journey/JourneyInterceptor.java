package com.example.quickvote.journey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC の HandlerInterceptor。Filter（Servlet の仕組み）より内側、Controller の直前・直後に挟まる。
 * DispatcherServlet が「どのメソッドが担当か」を決めたあとに呼ばれるので、担当のメソッド名が分かる。
 */
@Component
@Profile("journey")
public class JourneyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            Journey.step("Interceptor 前", "担当は " + method.getBeanType().getSimpleName() + "#"
                    + method.getMethod().getName());
        }
        return true; // false を返すと、ここで打ち切って Controller を呼ばない
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        Journey.step("Interceptor 後", "完了 status=" + response.getStatus());
    }
}
