import java.util.ArrayList;
import java.util.List;

record Option(String label) {
    Option {                                            // コンパクトコンストラクタ：検証の置き場所
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("選択肢の名前は必須です");
        }
        label = label.strip();                          // 正規化もここで
    }
}

record Poll(String title, List<Option> options) {}                  // 罠のある版

record SafePoll(String title, List<Option> options) {              // 防御的コピー版
    SafePoll {
        options = List.copyOf(options);                 // 不変のコピーに置き換える
    }
}

void main() {
    // --- record が自動で作るもの ---
    var a = new Option("  カレー ");
    var b = new Option("カレー");
    IO.println("toString = " + a);
    IO.println("a.label() = [" + a.label() + "]");
    IO.println("equals   = " + a.equals(b) + " / hashCode 一致 = " + (a.hashCode() == b.hashCode()));

    // --- 検証 ---
    try {
        new Option("   ");
    } catch (IllegalArgumentException e) {
        IO.println("検証エラー = " + e.getMessage());
    }

    // --- 罠：record でも中の List は変えられる ---
    var source = new ArrayList<>(List.of(new Option("カレー"), new Option("ラーメン")));
    var poll = new Poll("ランチ", source);
    source.add(new Option("勝手に追加"));
    poll.options().add(new Option("外からも追加"));
    IO.println("Poll の選択肢 = " + poll.options().size() + " 件");

    // --- 対策：コンストラクタで List.copyOf ---
    var source2 = new ArrayList<>(List.of(new Option("カレー"), new Option("ラーメン")));
    var safe = new SafePoll("ランチ", source2);
    source2.add(new Option("勝手に追加"));
    IO.println("SafePoll の選択肢 = " + safe.options().size() + " 件");
    try {
        safe.options().add(new Option("外から追加"));
    } catch (UnsupportedOperationException e) {
        IO.println("外からの追加は UnsupportedOperationException");
    }
}
