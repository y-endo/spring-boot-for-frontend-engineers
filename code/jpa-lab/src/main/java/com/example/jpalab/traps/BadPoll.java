package com.example.jpalab.traps;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.example.jpalab.poll.PollStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 【第41章の演習】AI が書きそうな JPA コード。罠が5つ入っている（答え合わせは TrapsTest）。実際には使わないこと。
 * 同じ polls テーブルを、別の Entity 名（BadPoll）で読み書きする。
 */
@Entity(name = "BadPoll")
@Table(name = "polls")
public class BadPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private PollStatus status;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<BadOption> options = new HashSet<>();

    protected BadPoll() {
    }

    public BadPoll(String title, Instant closesAt) {
        this.title = title;
        this.status = PollStatus.OPEN;
        this.closesAt = closesAt;
    }

    public void addOption(String label) {
        options.add(new BadOption(this, label));
    }

    public void close(Instant now) {
        this.status = PollStatus.CLOSED;
        this.closesAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public PollStatus getStatus() {
        return status;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public long getVersion() {
        return version;
    }

    public Set<BadOption> getOptions() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BadPoll other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Poll{id=" + id + ", title=" + title + ", status=" + status + ", options=" + options + "}";
    }
}
