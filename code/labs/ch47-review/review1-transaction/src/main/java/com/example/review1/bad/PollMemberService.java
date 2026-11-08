package com.example.review1.bad;

import java.io.IOException;

import com.example.review1.infra.ArchiveWriter;
import com.example.review1.infra.EventRecorder;
import com.example.review1.infra.PollMembers;
import com.example.review1.infra.WebhookClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollMemberService implements PollMembers {

    private static final Logger log = LoggerFactory.getLogger(PollMemberService.class);
    private static final int MAX_PARTICIPANTS = 50;

    private final MemberRepository members;
    private final WebhookClient webhook;
    private final EventRecorder events;
    private final ArchiveWriter archive;

    public PollMemberService(MemberRepository members, WebhookClient webhook, EventRecorder events,
            ArchiveWriter archive) {
        this.members = members;
        this.webhook = webhook;
        this.events = events;
        this.archive = archive;
    }

    @Override
    @Transactional
    public void join(long pollId, long visitorId) {
        int current = members.countParticipants(pollId);
        if (current >= MAX_PARTICIPANTS) {
            throw new IllegalStateException("参加者が上限に達しています");
        }
        members.insertParticipant(pollId, visitorId);
        webhook.notifyJoined(pollId, visitorId);
    }

    @Override
    @Transactional
    public void addOption(long pollId, String label) {
        members.insertOption(pollId, label);
        try {
            events.record(pollId, "OPTION_ADDED");
        } catch (Exception e) {
            log.warn("イベントの記録に失敗しました pollId={}", pollId, e);
        }
    }

    @Override
    @Transactional
    public void close(long pollId) throws IOException {
        members.markClosed(pollId);
        archive.writeSnapshot(pollId);
    }
}
