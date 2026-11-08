package com.example.quickvote.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.jspecify.annotations.Nullable;

/**
 * 時刻まわりの小さな部品。DB は timestamptz、Java は Instant で持つ。
 * JDBC には OffsetDateTime で渡すのが確実なので、境界でここを通して変換する。
 */
public final class Db {

    private Db() {
    }

    /** 現在時刻。PostgreSQL の timestamptz はマイクロ秒精度なので、Java側も揃えて丸める（往復で値が変わらない）。 */
    public static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public static @Nullable OffsetDateTime ts(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        Instant value = instantOrNull(rs, column);
        if (value == null) {
            throw new SQLException(column + " is NULL");
        }
        return value;
    }

    public static @Nullable Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
