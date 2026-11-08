package com.example.mybatislab;

import java.util.ArrayList;
import java.util.List;

public class PollWithOptions {

    private Long id;
    private String title;
    private List<OptionRow> options = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<OptionRow> getOptions() {
        return options;
    }

    public void setOptions(List<OptionRow> options) {
        this.options = options;
    }
}
