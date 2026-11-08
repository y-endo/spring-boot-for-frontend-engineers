interface Notifier {
    void send(String message);

    default void sendAll(String... messages) {      // interface にも標準実装（default）を書ける
        for (String m : messages) {
            send(m);
        }
    }
}

class ConsoleNotifier implements Notifier {
    @Override
    public void send(String message) {
        IO.println("[console] " + message);
    }
}

class PrefixedNotifier extends ConsoleNotifier {    // class は 1 つだけ継承できる
    private final String prefix;

    PrefixedNotifier(String prefix) {
        this.prefix = prefix;                       // 親のコンストラクタは暗黙に先に呼ばれる
    }

    @Override
    public void send(String message) {
        super.send(prefix + message);               // 親の実装を呼ぶ
    }
}

void main() {
    Notifier n = new PrefixedNotifier("QuickVote: ");   // 変数の型は interface、実体は子クラス
    n.sendAll("投票が作られました", "締切まで残り5分");
    IO.println("実体のクラス = " + n.getClass().getSimpleName());
    IO.println("ConsoleNotifier でもある? " + (n instanceof ConsoleNotifier));
}
