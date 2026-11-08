import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class StorageException extends RuntimeException {
    StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

String loadDescription(String file) {
    try {
        return Files.readString(Path.of(file));
    } catch (IOException e) {
        throw new StorageException("説明の読み込みに失敗: " + file, e);
    }
}

String showPoll(String code) {
    return "説明: " + loadDescription(code + ".txt");
}

void main() {
    IO.println(showPoll("AB12CD34"));       // ファイルは存在しない → 例外が main まで抜ける
}
