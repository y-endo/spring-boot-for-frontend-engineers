class Base {
    public void send(String message) { IO.println("base " + message); }
}

class Child extends Base {
    @Override
    public void sned(String message) { IO.println("child " + message); }   // typo
}

void main() {
    new Child().send("hi");
}
