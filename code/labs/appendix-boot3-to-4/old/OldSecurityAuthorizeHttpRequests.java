import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

class OldSecurityAuthorizeHttpRequests {
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests()
            .requestMatchers("/api/**").authenticated();
        return http.build();
    }
}
