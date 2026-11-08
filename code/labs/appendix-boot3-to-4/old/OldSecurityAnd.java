import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class OldSecurityAnd {
    void chain(HttpSecurity http) throws Exception {
        http.formLogin(f -> {}).and().httpBasic(b -> {});
    }
}
