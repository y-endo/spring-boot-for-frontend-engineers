import java.math.BigDecimal;
import java.math.RoundingMode;

void main() {
    // --- 2進数の浮動小数点 ---
    IO.println("0.1 + 0.2        = " + (0.1 + 0.2));
    IO.println("0.1 + 0.2 == 0.3 ? " + (0.1 + 0.2 == 0.3));
    IO.println("1 / 3 * 3 (double)= " + (1.0 / 3 * 3));

    // --- BigDecimal：コンストラクタの引数に注意 ---
    IO.println("new BigDecimal(0.1)     = " + new BigDecimal(0.1));
    IO.println("new BigDecimal(\"0.1\")   = " + new BigDecimal("0.1"));
    IO.println("BigDecimal.valueOf(0.1) = " + BigDecimal.valueOf(0.1));

    BigDecimal sum = new BigDecimal("0.1").add(new BigDecimal("0.2"));
    IO.println("0.1 + 0.2 (BigDecimal)  = " + sum + " / 0.3 と equals? " + sum.equals(new BigDecimal("0.3")));

    // --- equals は scale も比べる ---
    var two  = new BigDecimal("2.0");
    var two2 = new BigDecimal("2.00");
    IO.println("2.0 equals 2.00    = " + two.equals(two2));
    IO.println("2.0 compareTo 2.00 = " + two.compareTo(two2));

    // --- 割り算は丸め方を指定しないと例外 ---
    try {
        BigDecimal.ONE.divide(new BigDecimal(3));
    } catch (ArithmeticException e) {
        IO.println("1 / 3 → ArithmeticException: " + e.getMessage());
    }
    IO.println("1 / 3 (小数1桁, HALF_UP) = " + BigDecimal.ONE.divide(new BigDecimal(3), 1, RoundingMode.HALF_UP));

    // --- 丸めモード ---
    for (String s : new String[]{"2.5", "3.5", "-2.5"}) {
        var v = new BigDecimal(s);
        IO.println(s + " → HALF_UP=" + v.setScale(0, RoundingMode.HALF_UP)
                + " HALF_EVEN=" + v.setScale(0, RoundingMode.HALF_EVEN)
                + " DOWN=" + v.setScale(0, RoundingMode.DOWN));
    }

    // --- int のオーバーフローは例外にならない ---
    int big = Integer.MAX_VALUE;
    IO.println("MAX_VALUE + 1 = " + (big + 1));
    try {
        Math.addExact(big, 1);
    } catch (ArithmeticException e) {
        IO.println("Math.addExact → " + e.getMessage());
    }
}
