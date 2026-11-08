class Tally {
    static int total(int[] votes) {
        int sum = 0;
        for (int v : votes) {
            sum += v;
        }
        return sum;
    }
}
