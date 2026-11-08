import java.util.List;

void main() {
    List<Integer> nums = List.of(1, 2, 3);
    int sum = 0;
    nums.forEach(n -> sum += n);        // JavaScript なら普通に書ける
    IO.println(sum);
}
