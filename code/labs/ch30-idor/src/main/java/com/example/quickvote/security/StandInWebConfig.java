package com.example.quickvote.security;

import java.util.List;

import com.example.quickvote.common.QuickVoteProperties;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** StandInVisitorArgumentResolver を Spring MVC に登録する。第6部で、この2クラスごと消える。 */
@Configuration
public class StandInWebConfig implements WebMvcConfigurer {

    private final String headerName;

    public StandInWebConfig(QuickVoteProperties properties) {
        this.headerName = properties.standInAuth().header();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new StandInVisitorArgumentResolver(headerName));
    }
}
