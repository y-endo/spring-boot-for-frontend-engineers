// 第33章：「先頭が違う」と「末尾だけ違う」で、比較にかかる時間は変わるか。
// 実行：java scripts/TimingDemo.java
//
// Arrays.equals は「違う箇所が見つかった時点で false を返す」ので、先頭で違うほうが速い（理論上）。
// MessageDigest.isEqual は、長さが同じなら最後まで比べて結果をまとめる（定数時間）。
// 差は数十ナノ秒で、ネットワークの揺らぎ（ミリ秒）に埋もれて普通は測れない。それでも「秘密の比較には isEqual」と決めておけば、悩まなくてよい。
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

static final int N = 3_000_000;

byte[] token(char last, char first) {
    char[] c = new char[1024];
    Arrays.fill(c, 'a');
    c[0] = first;
    c[c.length - 1] = last;
    return new String(c).getBytes(StandardCharsets.UTF_8);
}

long time(java.util.function.BiPredicate<byte[], byte[]> eq, byte[] a, byte[] b) {
    int hits = 0;
    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
        if (eq.test(a, b)) {
            hits++;
        }
    }
    long elapsed = System.nanoTime() - start;
    if (hits < 0) {
        IO.println(hits); // 最適化で消されないように使う
    }
    return elapsed / N;
}

void main() {
    byte[] secret = token('a', 'a');
    byte[] diffFirst = token('a', 'b');   // 先頭だけ違う
    byte[] diffLast = token('b', 'a');    // 末尾だけ違う
    for (int round = 1; round <= 3; round++) {
        IO.println("--- " + round + " 回目（1回の比較あたりのナノ秒）");
        IO.println("Arrays.equals       先頭が違う: " + time(Arrays::equals, secret, diffFirst)
                + " ns / 末尾だけ違う: " + time(Arrays::equals, secret, diffLast) + " ns");
        IO.println("MessageDigest.isEqual 先頭が違う: " + time(MessageDigest::isEqual, secret, diffFirst)
                + " ns / 末尾だけ違う: " + time(MessageDigest::isEqual, secret, diffLast) + " ns");
    }
}
