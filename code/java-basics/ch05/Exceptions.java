import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PollNotFoundException extends RuntimeException {          // unchecked（業務上の例外）
    PollNotFoundException(String code) {
        super("投票が見つかりません: " + code);
    }
}

class StorageException extends RuntimeException {               // 低レベルの失敗を包む例外
    StorageException(String message, Throwable cause) {
        super(message, cause);                                  // cause を必ず引き継ぐ
    }
}

String loadDescription(String file) {
    try {
        return Files.readString(Path.of(file));                 // IOException は checked
    } catch (IOException e) {
        throw new StorageException("説明の読み込みに失敗: " + file, e);   // 包んで投げ直す
    }
}

void findPoll(String code) {
    throw new PollNotFoundException(code);
}

void main() {
    // (1) checked 例外：catch するか throws しないとコンパイルできない
    try {
        loadDescription("no-such-file.txt");
    } catch (StorageException e) {
        IO.println("message = " + e.getMessage());
        IO.println("cause   = " + e.getCause());
    }

    // (2) スタックトレース：どこで・どの経路で起きたか
    try {
        findPoll("ZZZZZZZZ");
    } catch (PollNotFoundException e) {
        e.printStackTrace(System.out);
    }

    // (3) finally は必ず実行される
    try {
        IO.println("処理中...");
        throw new IllegalStateException("途中で失敗");
    } catch (IllegalStateException e) {
        IO.println("catch: " + e.getMessage());
    } finally {
        IO.println("finally: 例外が出ても出なくても通る");
    }
}
