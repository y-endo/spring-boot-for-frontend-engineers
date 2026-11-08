package com.example.quickvote.poll;

/** 選択肢追加の応答。冪等性記録にJSONとして保存され、再送時にはそのまま返る。 */
public record OptionResponse(long id, String label) {
}
