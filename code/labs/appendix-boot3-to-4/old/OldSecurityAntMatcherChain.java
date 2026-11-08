import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class OldSecurityAntMatcherChain {
    void chain(HttpSecurity http) throws Exception {
        http.antMatcher("/api/**");
    }
}
