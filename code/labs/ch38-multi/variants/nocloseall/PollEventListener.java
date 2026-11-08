package com.example.quickvote.event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL の LISTEN/NOTIFY でインスタンス間に「新着あり」を伝える。
 * <p>
 * 専用スレッドが専用の接続を1本持ち、LISTEN したまま通知を待つ。
 * この接続は HikariCP のプールには入れない（プールの接続は貸し出して返すもので、
 * 長時間 LISTEN し続ける用途には向かない）。接続先は JdbcConnectionDetails から得るので、
 * Docker Compose 連携や Testcontainers（@ServiceConnection）で差し替わった接続先にも自動で追従する。
 * <p>
 * 複数台構成の限界: NOTIFY は「その瞬間 LISTEN していた接続」にしか届かない。
 * 切断中の通知は失われるため、再接続のたびに deliverAll() で全購読者を DB に追いつかせる。
 */
@Component
public class PollEventListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PollEventListener.class);

    /** getNotifications の待ち時間。停止要求に反応できる粒度でもある。 */
    private static final int POLL_TIMEOUT_MILLIS = 1000;
    private static final long RECONNECT_DELAY_MILLIS = 2000;

    private final JdbcConnectionDetails connectionDetails;
    private final PollEventHub hub;
    private volatile boolean running;
    private Thread thread;

    public PollEventListener(JdbcConnectionDetails connectionDetails, PollEventHub hub) {
        this.connectionDetails = connectionDetails;
        this.hub = hub;
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofPlatform().name("poll-event-listener").daemon().start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        // SSE の接続を先に閉じる（閉じないと graceful shutdown が接続の終了を待ち続ける）。
        // [lab] hub.closeAll() を呼ばない版（graceful shutdown が SSE に止められる様子を観察する）
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try (Connection connection = DriverManager.getConnection(connectionDetails.getJdbcUrl(),
                    connectionDetails.getUsername(), connectionDetails.getPassword())) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + PollEventRepository.CHANNEL);
                }
                log.info("LISTEN を開始しました: channel={}", PollEventRepository.CHANNEL);
                // LISTEN する前に起きた変更を取りこぼさないよう、LISTEN 後に一度追いつかせる。
                hub.deliverAll();
                PGConnection pgConnection = connection.unwrap(PGConnection.class);
                while (running) {
                    PGNotification[] notifications = pgConnection.getNotifications(POLL_TIMEOUT_MILLIS);
                    if (notifications == null) {
                        continue;
                    }
                    for (PGNotification notification : notifications) {
                        hub.onNotify(Long.parseLong(notification.getParameter()));
                    }
                }
            } catch (SQLException | RuntimeException e) {
                if (running) {
                    log.warn("LISTEN 接続でエラーが発生しました。再接続します: {}", e.getClass().getSimpleName());
                    sleepQuietly();
                }
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(RECONNECT_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
