import java.util.concurrent.CountDownLatch;

/** Spring では Service は 1 つだけ作られ、全リクエストで共有される。それを模したクラス。 */
class GreetingService {
    private String currentVoter;                       // 悪い例：リクエスト固有の値をフィールドに置いた

    String greetBad(String voter) throws InterruptedException {
        this.currentVoter = voter;                     // (1) 自分の名前を書き込む
        Thread.sleep(100);                             // (2) DB 問い合わせなどで待つ。その間に別スレッドが来る
        return currentVoter + " さん、投票を受け付けました"; // (3) 読み戻す。他人の名前に変わっているかもしれない
    }

    String greetGood(String voter) throws InterruptedException {
        String name = voter;                           // ローカル変数：スレッドごとに別の箱
        Thread.sleep(100);
        return name + " さん、投票を受け付けました";
    }
}

void main() throws InterruptedException {
    GreetingService service = new GreetingService();   // 1 つだけ作って共有する
    CountDownLatch done = new CountDownLatch(2);

    for (String voter : new String[] {"alice", "bob"}) {
        Thread.ofPlatform().name("request-" + voter).start(() -> {
            try {
                String bad = service.greetBad(voter);
                IO.println("[" + Thread.currentThread().getName() + "] フィールド版  : " + bad);
                String good = service.greetGood(voter);
                IO.println("[" + Thread.currentThread().getName() + "] ローカル変数版: " + good);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        Thread.sleep(30);                              // alice の処理中に bob が入ってくる、をわざと作る
    }
    done.await();
}
