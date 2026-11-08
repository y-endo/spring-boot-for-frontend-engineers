package com.example.loginauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** フォームログイン + HttpSession（Cookie の JSESSIONID）。学習用にユーザーはメモリ上。 */
@Configuration
@Profile("form")
class FormLoginConfig {

    @Bean
    SecurityFilterChain formChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults())                       // GET /login の画面と POST /login の処理
            .logout(Customizer.withDefaults())                          // POST /logout（CSRF トークン必須）
            .sessionManagement(s -> s.maximumSessions(1));              // 同じユーザーの同時ログインは 1 つまで
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        UserDetails alice = User.withUsername("alice")
                .password(encoder.encode("s3cret-pass"))                // 保存するのはハッシュ
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(alice);
    }
}
