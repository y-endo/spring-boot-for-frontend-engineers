import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

class Percentages {

    /** 素朴な実装：それぞれを四捨五入するだけ（合計が 100 にならないことがある） */
    static int[] naive(int[] counts) {
        long total = Arrays.stream(counts).asLongStream().sum();
        int[] result = new int[counts.length];
        if (total == 0) return result;
        for (int i = 0; i < counts.length; i++) {
            result[i] = (int) Math.round(counts[i] * 100.0 / total);
        }
        return result;
    }

    /**
     * 最大剰余法：合計がちょうど target になるように整数で配分する。
     * 同じ剰余なら票数が多い方、それも同じなら先の選択肢を優先する。
     */
    static int[] allocate(int[] counts, int target) {
        long total = 0;
        for (int c : counts) {
            if (c < 0) throw new IllegalArgumentException("票数が負です: " + c);
            total += c;
        }
        int[] result = new int[counts.length];
        if (total == 0) return result;                     // 0票のときは全員 0

        long[] remainder = new long[counts.length];
        int assigned = 0;
        for (int i = 0; i < counts.length; i++) {
            long scaled = (long) counts[i] * target;
            result[i] = (int) (scaled / total);            // 切り捨て
            remainder[i] = scaled % total;                 // 切り捨てた端数（分子）
            assigned += result[i];
        }

        int leftover = target - assigned;                  // 配り切れていない分
        Integer[] order = IntStream.range(0, counts.length).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator
                .comparingLong((Integer i) -> remainder[i]).reversed()
                .thenComparing(Comparator.comparingInt((Integer i) -> counts[i]).reversed()));
        for (int k = 0; k < leftover; k++) {
            result[order[k]]++;
        }
        return result;
    }

    /** 画面表示用：配分された整数（0.1% 単位）を "33.4" のような文字列にする */
    static String format(int permille) {
        return BigDecimal.valueOf(permille).movePointLeft(1).setScale(1, RoundingMode.UNNECESSARY).toPlainString();
    }

    void main() {
        int[] counts = {1, 1, 1};
        IO.println("票数              = " + Arrays.toString(counts));
        IO.println("naive (%)         = " + Arrays.toString(naive(counts)) + " 合計 " + Arrays.stream(naive(counts)).sum());
        IO.println("allocate (%)      = " + Arrays.toString(allocate(counts, 100)));

        int[] c2 = {5, 3, 2, 0};
        IO.println("票数              = " + Arrays.toString(c2));
        IO.println("allocate (%)      = " + Arrays.toString(allocate(c2, 100)));

        int[] c3 = {1, 1, 1};
        int[] permille = allocate(c3, 1000);               // 0.1% 単位で配る
        IO.println("0.1%単位           = " + Arrays.toString(permille)
                + " → " + Arrays.stream(permille).mapToObj(Percentages::format).toList());

        int[] c4 = {2, 3, 3};                              // naive で 101 になる例
        IO.println("票数              = " + Arrays.toString(c4));
        IO.println("naive (%)         = " + Arrays.toString(naive(c4)) + " 合計 " + Arrays.stream(naive(c4)).sum());
        IO.println("allocate (%)      = " + Arrays.toString(allocate(c4, 100)));
    }
}
