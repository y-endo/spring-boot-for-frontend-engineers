import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

// TS: class NotFoundError extends Error { constructor(readonly id: string, options?: { cause: unknown }) {...} }
class NotFoundException extends RuntimeException {      // unchecked（throws 宣言不要）
    private final String id;
    NotFoundException(String id, Throwable cause) {
        super("not found: " + id, cause);
        this.id = id;
    }
    String id() { return id; }
}

// checked 例外は throws の宣言が必須
String readFirstLine(String text) throws IOException {
    try (var r = new BufferedReader(new StringReader(text))) {    // TS の finally での後始末 / using
        return r.readLine();
    }
}

class Res implements AutoCloseable {
    final String name;
    Res(String name) { this.name = name; IO.println("open " + name); }
    public void close() { IO.println("close " + name); }
}

void main() throws Exception {
    try {
        throw new NotFoundException("p1", new IllegalStateException("db"));
    } catch (NotFoundException e) {
        IO.println(e.getMessage() + " id=" + e.id() + " cause=" + e.getCause().getMessage());
    } finally {
        IO.println("finally");
    }

    // 複数の型を1つの catch で（TS では instanceof で分岐）
    try {
        Integer.parseInt("x");
    } catch (NumberFormatException | NullPointerException e) {
        IO.println("multi-catch: " + e.getClass().getSimpleName());
    }

    IO.println(readFirstLine("line1\nline2"));

    // try-with-resources は宣言と逆順に close
    try (var a = new Res("A"); var b = new Res("B")) {
        IO.println("body");
        throw new RuntimeException("in body");
    } catch (RuntimeException e) {
        IO.println("caught " + e.getMessage());
    }

    // new Error("wrapped", { cause })
    try {
        try { Integer.parseInt("x"); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("wrapped", e); }
    } catch (IllegalArgumentException e) {
        IO.println(e.getMessage() + " <- " + e.getCause().getClass().getName());
    }

    // 配列の範囲外は実行時例外（TS は undefined が返るだけ）
    try { int[] arr = new int[1]; IO.println(arr[3]); }
    catch (ArrayIndexOutOfBoundsException e) { IO.println(e.getMessage()); }
}
