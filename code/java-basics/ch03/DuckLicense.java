interface Quacker {
    String quack();
}

class Duck implements Quacker {
    public String quack() { return "ガーガー"; }
}

class Robot implements Quacker {    // 「Quackerです」と名乗る（免許を取る）
    public String quack() { return "ピー、ガー"; }
}

void listen(Quacker q) {
    IO.println(q.quack());
}

void main() {
    listen(new Duck());
    listen(new Robot());
}
