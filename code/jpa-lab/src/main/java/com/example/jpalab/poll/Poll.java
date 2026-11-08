package com.example.jpalab.poll;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** polls テーブルの1行を表す Entity。 */
@Entity
@Table(name = "polls")
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PollStatus status;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    /** 楽観ロック。UPDATE のたびに Hibernate が 1 増やし、WHERE version = ? で衝突を検出する。 */
    @Version
    private long version;

    /** 1対多の「1」側。外部キーは options.poll_id にあるので mappedBy で「向こうが持ち主」と宣言する。 */
    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Option> options = new ArrayList<>();

    /** JPA が使う引数なしコンストラクタ。アプリのコードからは呼ばせない。 */
    protected Poll() {
    }

    public Poll(String title, Instant closesAt) {
        this.title = title;
        this.status = PollStatus.OPEN;
        this.closesAt = closesAt;
    }

    public Option addOption(String label) {
        Option option = new Option(this, label);
        options.add(option);
        return option;
    }

    public void rename(String newTitle) {
        this.title = newTitle;
    }

    public void close(Instant now) {
        this.status = PollStatus.CLOSED;
        if (closesAt.isAfter(now)) {
            this.closesAt = now;
        }
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

    public List<Option> getOptions() {
        return Collections.unmodifiableList(options);
    }

    /** 関連（options）を含めない。含めると遅延ロードが走り、双方向なら循環する。 */
    @Override
    public String toString() {
        return "Poll{id=" + id + ", title=" + title + ", status=" + status + "}";
    }
}
