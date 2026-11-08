package com.example.mybatislab;

/** getter / setter 形式の JavaBean。日本の現場の MyBatis では、今もこの形をよく見る。 */
public class PollRow {

    private Long id;
    private String title;
    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
