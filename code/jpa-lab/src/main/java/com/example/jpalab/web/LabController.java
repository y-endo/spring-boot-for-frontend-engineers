package com.example.jpalab.web;

import java.util.List;

import javax.sql.DataSource;

import com.example.jpalab.poll.Option;
import com.example.jpalab.poll.Poll;
import com.example.jpalab.poll.PollService;
import com.example.jpalab.poll.PollView;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** open-in-view（OSIV）の挙動を観察するための API。 */
@RestController
@RequestMapping("/api/lab")
public class LabController {

    private static final Logger lab = LoggerFactory.getLogger("lab");

    private final PollService polls;
    private final DataSource dataSource;

    public LabController(PollService polls, DataSource dataSource) {
        this.polls = polls;
        this.dataSource = dataSource;
    }

    /**
     * 悪い例: Service が返した Entity の遅延ロードを、Controller で発動させる。
     * open-in-view が true なら動く。false なら LazyInitializationException になる。
     */
    @GetMapping("/polls/{id}/option-labels")
    public List<String> optionLabels(@PathVariable Long id) {
        Poll poll = polls.findEntity(id);
        lab.info("controller: トランザクション中={}, 使用中の接続数={}",
                TransactionSynchronizationManager.isActualTransactionActive(), activeConnections());
        return poll.getOptions().stream().map(Option::getLabel).toList();
    }

    /** 良い例: Service が、トランザクションの中で必要な形（DTO）まで作って返す。 */
    @GetMapping("/polls/{id}")
    public PollView view(@PathVariable Long id) {
        PollView view = polls.findView(id);
        lab.info("controller: トランザクション中={}, 使用中の接続数={}",
                TransactionSynchronizationManager.isActualTransactionActive(), activeConnections());
        return view;
    }

    private int activeConnections() {
        return dataSource instanceof HikariDataSource hikari ? hikari.getHikariPoolMXBean().getActiveConnections() : -1;
    }
}
