package com.example.quickvote.poll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 参加コードは前後の空白・小文字を許す（JoinCodeGenerator.normalize で整える）。極端に長い入力だけここで弾く。 */
public record JoinRequest(@NotBlank @Size(max = 32) String code) {
}
