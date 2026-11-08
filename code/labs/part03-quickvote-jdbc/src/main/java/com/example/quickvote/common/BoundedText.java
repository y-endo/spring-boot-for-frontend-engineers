package com.example.quickvote.common;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 文字数（コードポイント数）と UTF-8 のバイト数の上限を検証する。
 * <p>
 * 標準の @Size は UTF-16 の「char の個数」を数える。絵文字などは1文字で char が2個なので、
 * 「100文字まで」を @Size(max = 100) と書くと、絵文字だけの文字列は51文字で弾かれる。
 * そこで人間が数える文字（コードポイント）で数える制約を自作している。
 * null は検証しない（必須かどうかは @NotBlank に任せる）。
 */
@Documented
@Constraint(validatedBy = BoundedTextValidator.class)
@Target({ FIELD, METHOD, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface BoundedText {

    String message() default "文字数またはバイト数が上限を超えています";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int maxChars();

    /** UTF-8 でのバイト数の上限。指定しなければ制限しない。 */
    int maxBytes() default Integer.MAX_VALUE;
}
