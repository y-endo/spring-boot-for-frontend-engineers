package com.example.jpalab.traps;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 【第41章の演習】BadPoll と対になる選択肢。 */
@Entity(name = "BadOption")
@Table(name = "options")
public class BadOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "poll_id")
    @JsonIgnore
    private BadPoll poll;

    private String label;

    protected BadOption() {
    }

    BadOption(BadPoll poll, String label) {
        this.poll = poll;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public BadPoll getPoll() {
        return poll;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BadOption other)) {
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
        return "Option{id=" + id + ", label=" + label + ", poll=" + poll + "}";
    }
}
