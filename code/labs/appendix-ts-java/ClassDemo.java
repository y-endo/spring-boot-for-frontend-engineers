import java.util.ArrayList;
import java.util.List;

// TS: interface Notifier { send(msg: string): void }
interface Notifier {
    void send(String msg);
    default void sendAll(List<String> msgs) { msgs.forEach(this::send); }
}

// TS: abstract class Base { abstract name(): string; ... }
abstract class Base implements Notifier {
    protected final List<String> log = new ArrayList<>();
    abstract String name();
    public void send(String msg) { log.add(name() + ":" + msg); }
}

// TS: class Poll { static readonly MAX_OPTIONS = 10; #count = 0; constructor(readonly title: string) {} }
class Poll {
    static final int MAX_OPTIONS = 10;           // static readonly
    private int count;                           // private（実行時にも強制）
    private final String title;                  // readonly
    Poll(String title) { this.title = title; }
    int count() { return count; }                // getter（get count()）
    void vote() { count++; }
    String title() { return title; }
}

class Console extends Base {
    String name() { return "console"; }
}

// TS: const config = { host: "x", port: 1 } as const  →  record
record Config(String host, int port) {
    Config {                                     // コンパクトコンストラクタ（検証）
        if (port < 1) throw new IllegalArgumentException("port");
    }
    Config withPort(int p) { return new Config(host, p); }   // { ...config, port: p } 相当
}

void main() {
    var p = new Poll("lunch");
    p.vote();
    IO.println(p.title() + " " + p.count() + " max=" + Poll.MAX_OPTIONS);

    var c = new Console();
    c.sendAll(List.of("a", "b"));
    IO.println(c.log);

    var cfg = new Config("localhost", 80);
    IO.println(cfg + " -> " + cfg.withPort(8080));

    Notifier anon = msg -> IO.println("anon:" + msg);   // 抽象メソッドが1つなのでラムダで書ける
    anon.send("hi");

    Object o = cfg;
    String desc = switch (o) {                           // instanceof の連鎖の switch 版
        case Config(String h, int port) when port == 80 -> "http " + h;
        case Config cc -> "other " + cc.host();
        default -> "unknown";
    };
    IO.println(desc);
}
