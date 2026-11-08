package com.example.jpalab.traps;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 【第41章の演習】AI が書きそうな Service。 */
@Service
public class BadPollService {

    private static final Logger log = LoggerFactory.getLogger(BadPollService.class);

    private final BadPollRepository polls;
    private final BadOptionRepository options;

    public BadPollService(BadPollRepository polls, BadOptionRepository options) {
        this.polls = polls;
        this.options = options;
    }

    @Transactional
    public Long create(String title, Instant closesAt, List<String> labels) {
        BadPoll poll = new BadPoll(title, closesAt);
        labels.forEach(poll::addOption);
        return polls.save(poll).getId();
    }

    public List<BadPoll> findAll() {
        return polls.findAll();
    }

    /** 選択肢IDの一覧から、ラベルの一覧を返す。 */
    @Transactional(readOnly = true)
    public List<String> labelsOf(List<Long> optionIds) {
        List<String> labels = new ArrayList<>();
        for (Long optionId : optionIds) {
            BadOption option = options.findById(optionId).orElseThrow();
            labels.add(option.getLabel());
        }
        return labels;
    }

    @Transactional
    public void close(Long id, Instant now) {
        BadPoll poll = polls.findById(id).orElseThrow();
        poll.close(now);
        log.info("closed: {}", poll);
    }
}
