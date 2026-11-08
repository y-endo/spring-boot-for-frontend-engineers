package com.example.quickvote.invite;

import java.time.Instant;

/** 招待の発行結果。token は発行時のこの応答でしか分からない（DB にはハッシュしかない）。 */
public record InviteResponse(String token, Instant expiresAt) {
}
