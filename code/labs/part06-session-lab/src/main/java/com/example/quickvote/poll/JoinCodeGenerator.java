package com.example.quickvote.poll;

import java.security.SecureRandom;
import java.util.Locale;

import com.example.quickvote.security.LabProperties;

import org.springframework.stereotype.Component;

/**
 * 参加コード（8文字）の生成。
 * 0/O、1/I/L のように見間違えやすい文字を除いた31種（英大文字23＋数字2〜9の8）。
 * 31^8 ≒ 8.5×10^11 通りあるが、短いので総当たりの対象になる。だから試行回数制限が別に必要。
 * <p>
 * このラボだけの差：長さを lab.join-code-length で変えられる（第33章で、3文字にして総当たりで破る）。仕様は 8。
 */
@Component
public class JoinCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final int length;
    private final SecureRandom random = new SecureRandom();

    public JoinCodeGenerator(LabProperties lab) {
        this.length = lab.joinCodeLength();
    }

    public String generate() {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // nextInt(bound) は偏りなく 0..bound-1 を返す。
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** 利用者の入力ゆれ（前後の空白・小文字）を吸収する。 */
    public static String normalize(String input) {
        return input.strip().toUpperCase(Locale.ROOT);
    }

    /** 形式が正しいか。形式が違うコードは、DB に問い合わせるまでもなく「存在しない」。 */
    public boolean isWellFormed(String code) {
        if (code.length() != length) {
            return false;
        }
        return code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }
}
