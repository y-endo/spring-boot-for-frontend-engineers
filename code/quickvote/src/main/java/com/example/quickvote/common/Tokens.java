package com.example.quickvote.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 訪問者トークン・招待トークンの生成とハッシュ化。
 * トークン本体はクライアントにだけ渡し、DB には SHA-256 のハッシュ（16進64文字）だけを保存する。
 * DB が漏れても、そのままではなりすましに使えないようにするため。
 */
public final class Tokens {

    /** SecureRandom はスレッドセーフ。使い回してよい。java.util.Random は予測できるので使わない。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** newToken() が作る形（Base64URL の43文字）。 */
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private Tokens() {
    }

    /**
     * Cookie など外から来た値が、newToken() で作ったトークンの形か。
     * DB を引いたり、制限用のバケットを作ったりする前に、形が違うものを捨てるために使う
     * （出まかせの値ごとにメモリや検索を消費させないため）。
     */
    public static boolean isWellFormed(String value) {
        return TOKEN_FORMAT.matcher(value).matches();
    }

    /** 32バイト（256ビット）の乱数を Base64URL（パディングなし）にした43文字。総当たりでは当たらない長さ。 */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は Java の仕様上すべての実装が持つ。ここに来たら環境が壊れている。
            throw new IllegalStateException(e);
        }
    }
}
