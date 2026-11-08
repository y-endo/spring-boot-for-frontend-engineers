package com.example.review2.infra;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** メモリ上の投票。主催者（hostId）と参加者（members）を持つ。 */
public class PollData {

    private final long id;
    private final String title;
    private final long hostId;
    private final Set<Long> members = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public PollData(long id, String title, long hostId) {
        this.id = id;
        this.title = title;
        this.hostId = hostId;
        this.members.add(hostId);
    }

    public long id() {
        return id;
    }

    public String title() {
        return title;
    }

    public long hostId() {
        return hostId;
    }

    public boolean isMember(long visitorId) {
        return members.contains(visitorId);
    }

    public void addMember(long visitorId) {
        members.add(visitorId);
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        this.closed = true;
    }
}
