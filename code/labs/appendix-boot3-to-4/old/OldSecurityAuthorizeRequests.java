import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class OldSecurityAuthorizeRequests {
    void chain(HttpSecurity http) throws Exception {
        http.authorizeRequests();
    }
}
