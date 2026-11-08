import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class OldSecurityCsrfChain {
    void chain(HttpSecurity http) throws Exception {
        http.csrf().disable();
    }
}
