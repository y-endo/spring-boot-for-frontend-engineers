package com.example.quickvote.vote;

import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull Long optionId) {
}
