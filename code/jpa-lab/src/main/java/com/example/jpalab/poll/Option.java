package com.example.jpalab.poll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** options テーブルの1行。@Version は付けていない（楽観ロックなしの場合の挙動を第40章で比べるため）。 */
@Entity
@Table(name = "options")
public class Option {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 多対1の「多」側。@ManyToOne の既定は EAGER なので、必ず LAZY と書く。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id")
    private Poll poll;

    @Column(nullable = false)
    private String label;

    protected Option() {
    }

    Option(Poll poll, String label) {
        this.poll = poll;
        this.label = label;
    }

    public void relabel(String newLabel) {
        this.label = newLabel;
    }

    public Long getId() {
        return id;
    }

    public Poll getPoll() {
        return poll;
    }

    public String getLabel() {
        return label;
    }

    // equals / hashCode の方針（第41章）:
    // 主キーが null（まだ保存していない）の間は「どれとも等しくない」。hashCode は定数にして、保存で値が変わらないようにする。
    // instanceof を使うのは、Hibernate のプロキシ（Option のサブクラス）とも比較できるようにするため。
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Option that && getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Option.class.hashCode();
    }

    /** 関連（poll）を含めない。含めると遅延ロードが走り、双方向なら循環する。 */
    @Override
    public String toString() {
        return "Option{id=" + id + ", label=" + label + "}";
    }
}
