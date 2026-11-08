interface Quacker {
    String quack();
}

class Duck implements Quacker {
    public String quack() { return "ガーガー"; }
}

class Robot {                       // quack() は持っているが、Quacker を「名乗って」いない
    public String quack() { return "ピー、ガー"; }
}

void listen(Quacker q) {
    IO.println(q.quack());
}

void main() {
    listen(new Duck());
    listen(new Robot());            // TypeScript なら通る。Java はここでコンパイルエラー
}
