package com.example.loginauth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** JWT の中身は暗号化ではなく署名：誰でも読める。改ざんすると検証で落ちる。 */
public final class JwtLab {

    private JwtLab() {}

    public static void main(String[] args) {
        SecretKey key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user-42")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("scope", "read")
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        String token = jwt.getTokenValue();

        String[] parts = token.split("\\.");
        System.out.println("parts = " + parts.length);
        System.out.println("header  = " + new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        System.out.println("payload = " + new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));

        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        System.out.println("検証: sub=" + decoder.decode(token).getSubject());

        // 署名の改ざん（payload の sub を書き換える）
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\",\"scope\":\"read\"}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];
        try {
            decoder.decode(forged);
            System.out.println("改ざん: 通ってしまった");
        } catch (Exception e) {
            System.out.println("改ざん: 拒否 -> " + e.getClass().getSimpleName());
        }

        // 期限切れ（15 分トークンを 16 分後の時刻で検証する代わりに、過去に発行したトークンを作る）
        Instant past = now.minus(Duration.ofHours(1));
        JwtClaimsSet oldClaims = JwtClaimsSet.builder().subject("user-42")
                .issuedAt(past).expiresAt(past.plus(Duration.ofMinutes(15))).build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), oldClaims)).getTokenValue();
        try {
            decoder.decode(expired);
            System.out.println("期限切れ: 通ってしまった");
        } catch (Exception e) {
            System.out.println("期限切れ: 拒否 -> " + e.getClass().getSimpleName());
        }
    }
}
