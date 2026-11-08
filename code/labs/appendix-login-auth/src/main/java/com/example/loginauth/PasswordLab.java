package com.example.loginauth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** パスワードの保存：ハッシュ化（BCrypt / Argon2）と DelegatingPasswordEncoder の観察 */
public final class PasswordLab {

    private PasswordLab() {}

    public static void main(String[] args) {
        String raw = "correct horse battery staple";

        PasswordEncoder bcrypt = new BCryptPasswordEncoder();                        // cost 10（既定）
        String h1 = bcrypt.encode(raw);
        String h2 = bcrypt.encode(raw);
        System.out.println("bcrypt #1 = " + h1);
        System.out.println("bcrypt #2 = " + h2);
        System.out.println("同じ入力でも毎回違う（ソルトが入る）: " + !h1.equals(h2));
        System.out.println("matches(正) = " + bcrypt.matches(raw, h1) + " / matches(誤) = " + bcrypt.matches("wrong", h1));

        PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String a = argon2.encode(raw);
        System.out.println("argon2    = " + a);
        System.out.println("matches(正) = " + argon2.matches(raw, a));

        PasswordEncoder delegating = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String d = delegating.encode(raw);
        System.out.println("delegating = " + d);                         // 先頭の {bcrypt} が方式の目印
        System.out.println("別方式のハッシュも目印で判別して照合: " + delegating.matches(raw, "{bcrypt}" + h1));

        // BCrypt は 72 バイトまでしか見ない（仕様上の制限）。Spring Security 7 での挙動を観察する
        String longPw = "a".repeat(80);
        try {
            String hh = bcrypt.encode(longPw);
            System.out.println("80 文字: 受理 / 先頭72文字だけで一致? " + bcrypt.matches("a".repeat(72), hh));
        } catch (IllegalArgumentException e) {
            System.out.println("80 文字: 拒否 (" + e.getMessage() + ")");
        }
    }
}
