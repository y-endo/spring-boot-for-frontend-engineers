import java.util.ArrayList;
import java.util.List;

final List<String> saved = new ArrayList<>();

void save(String s) {
    if (s.isBlank()) throw new IllegalArgumentException("空文字は保存できません");
    saved.add(s);
}

boolean saveBad(String s) {          // 悪い例：握りつぶす
    try {
        save(s);
        return true;
    } catch (Exception e) {
        // 何もしない
        return false;
    }
}

void saveGood(String s) {            // 良い例：回復できないなら投げ直す
    try {
        save(s);
    } catch (IllegalArgumentException e) {
        throw new IllegalStateException("保存に失敗: [" + s + "]", e);
    }
}

void main() {
    IO.println("saveBad の結果 = " + saveBad(" ") + "（理由は誰にも分からない）");
    try {
        saveGood(" ");
    } catch (IllegalStateException e) {
        IO.println("saveGood → " + e.getMessage() + " / 原因 = " + e.getCause().getMessage());
    }
}
