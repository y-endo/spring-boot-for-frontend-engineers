class Conn implements AutoCloseable {
    private final String name;
    private final boolean failOnClose;

    Conn(String name, boolean failOnClose) {
        this.name = name;
        this.failOnClose = failOnClose;
        IO.println("open  " + name);
    }

    void use(boolean fail) {
        IO.println("use   " + name);
        if (fail) throw new IllegalStateException("use で失敗: " + name);
    }

    @Override
    public void close() {
        IO.println("close " + name);
        if (failOnClose) throw new IllegalStateException("close で失敗: " + name);
    }
}

void main() {
    IO.println("--- 正常系：宣言と逆順に close される ---");
    try (var a = new Conn("A", false); var b = new Conn("B", false)) {
        a.use(false);
        b.use(false);
    }

    IO.println("--- 本体で例外：それでも close される ---");
    try (var a = new Conn("A", false)) {
        a.use(true);
    } catch (IllegalStateException e) {
        IO.println("catch: " + e.getMessage());
    }

    IO.println("--- 本体と close の両方で例外：本体が主、close は suppressed ---");
    try (var c = new Conn("C", true)) {
        c.use(true);
    } catch (IllegalStateException e) {
        IO.println("主たる例外: " + e.getMessage());
        for (Throwable s : e.getSuppressed()) {
            IO.println("抑制された例外: " + s.getMessage());
        }
    }
}
