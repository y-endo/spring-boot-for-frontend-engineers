import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.text.Normalizer;

int graphemes(String s) {                         // 人間が「1文字」と数える単位
    BreakIterator it = BreakIterator.getCharacterInstance();
    it.setText(s);
    int n = 0;
    while (it.next() != BreakIterator.DONE) n++;
    return n;
}

void show(String name, String s) {
    IO.println(String.format("%-8s length=%-3d codePoints=%-3d graphemes=%-3d UTF-8 bytes=%d",
            name, s.length(), s.codePointCount(0, s.length()), graphemes(s),
            s.getBytes(StandardCharsets.UTF_8).length));
}

boolean validLabel(String label) {                // QuickVote の選択肢ラベルの規則
    int codePoints = label.codePointCount(0, label.length());   // 仕様の「文字数」はコードポイント数
    int bytes = label.getBytes(StandardCharsets.UTF_8).length;
    return 1 <= codePoints && codePoints <= 100 && bytes <= 400;
}

void main() {
    show("ASCII",   "curry");
    show("日本語",   "カレー");
    show("絵文字",   "😀");
    show("異体字",    "𠮷野家");                      // 「𠮷」は U+20BB7（サロゲートペア）
    show("国旗",     "🇯🇵");
    show("家族",     "👨‍👩‍👧");                       // 3人の絵文字を ZWJ でつないだ 1 文字

    String nfc = "が";                                // U+304C（1コードポイント）
    String nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD);   // か + 濁点（2コードポイント）
    show("が(NFC)", nfc);
    show("が(NFD)", nfd);
    IO.println("見た目は同じ 'が' だが equals = " + nfc.equals(nfd)
            + " / 正規化後は " + nfc.equals(Normalizer.normalize(nfd, Normalizer.Form.NFC)));

    IO.println("--- 規則：1-100コードポイント かつ UTF-8 で 400 バイト以内 ---");
    IO.println("日本語100文字    : " + validLabel("あ".repeat(100)) + " (bytes=300)");
    IO.println("日本語101文字    : " + validLabel("あ".repeat(101)));
    IO.println("絵文字😀100個    : " + validLabel("😀".repeat(100)) + " (bytes=400)");
    String family = "👨‍👩‍👧".repeat(20);
    IO.println("家族絵文字20個   : " + validLabel(family)
            + " (graphemes=" + graphemes(family) + ", codePoints=" + family.codePointCount(0, family.length()) + ")");
}
