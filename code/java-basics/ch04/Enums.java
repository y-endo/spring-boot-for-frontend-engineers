enum Role {
    HOST(true), MEMBER(false);

    private final boolean canClose;

    Role(boolean canClose) { this.canClose = canClose; }

    boolean canClose() { return canClose; }
}

String label(Role role) {
    return switch (role) {                // enum も default なしで網羅チェックされる
        case HOST   -> "主催者";
        case MEMBER -> "参加者";
    };
}

void main() {
    for (Role r : Role.values()) {
        IO.println(r + " (" + label(r) + ") 締切を操作できる? " + r.canClose() + " / ordinal=" + r.ordinal());
    }
    IO.println("valueOf(\"HOST\") = " + Role.valueOf("HOST"));
    IO.println("== で比較できる: " + (Role.valueOf("MEMBER") == Role.MEMBER));
    try {
        Role.valueOf("host");             // 大文字小文字は区別される
    } catch (IllegalArgumentException e) {
        IO.println("valueOf(\"host\") → " + e.getMessage());
    }
}
