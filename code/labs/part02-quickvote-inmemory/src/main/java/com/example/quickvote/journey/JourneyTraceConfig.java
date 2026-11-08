package com.example.quickvote.journey;

import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller・Service・Repository のメソッド呼び出しを、入れ子の深さつきでログに出す仕掛け。
 * Bean が作られた直後に「本物を包む代理（プロキシ）」へ差し替えている。この仕組みは第23章（@Transactional）の正体でもある。
 * 今は「メソッドの出入りを記録する覆いを被せている」と思えばよい。
 */
@Configuration
@Profile("journey")
public class JourneyTraceConfig {

    @Bean
    static BeanPostProcessor journeyTracer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                Class<?> type = bean.getClass();
                boolean target = type.getName().startsWith("com.example.quickvote.")
                        && !type.getName().startsWith("com.example.quickvote.journey.")
                        && (type.isAnnotationPresent(RestController.class)
                                || type.isAnnotationPresent(Service.class)
                                || type.isAnnotationPresent(Repository.class));
                if (!target) {
                    return bean;
                }
                ProxyFactory factory = new ProxyFactory(bean);
                factory.setProxyTargetClass(true); // インターフェースがないので、クラスを継承した代理を作る
                factory.addAdvice((MethodInterceptor) invocation -> {
                    int depth = Journey.enter();
                    Journey.step("呼び出し", "  ".repeat(depth) + invocation.getMethod().getDeclaringClass().getSimpleName()
                            + "." + invocation.getMethod().getName());
                    try {
                        return invocation.proceed();
                    } finally {
                        Journey.exit();
                    }
                });
                return factory.getProxy();
            }
        };
    }
}
