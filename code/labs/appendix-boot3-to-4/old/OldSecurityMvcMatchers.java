import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class OldSecurityMvcMatchers {
    void chain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.mvcMatchers("/api/**").authenticated());
    }
}
