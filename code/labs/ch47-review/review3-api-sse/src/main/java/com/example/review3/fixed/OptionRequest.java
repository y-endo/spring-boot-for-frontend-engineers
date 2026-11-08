package com.example.review3.fixed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 選択肢の追加。入力の形は境界で検証する（DB の制約は最終防衛線）。 */
public record OptionRequest(@NotBlank @Size(max = 100) String label) {
}
