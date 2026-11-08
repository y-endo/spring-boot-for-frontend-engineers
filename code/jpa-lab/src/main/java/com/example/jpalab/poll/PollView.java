package com.example.jpalab.poll;

import java.util.List;

/** 一覧画面に出したい形（投票のタイトルと、選択肢のラベル）。Entity ではなく、必要な形だけを持つ DTO。 */
public record PollView(Long id, String title, List<String> optionLabels) {
}
