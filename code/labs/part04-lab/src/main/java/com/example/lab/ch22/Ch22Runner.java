package com.example.lab.ch22;

import com.example.lab.LabRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** --lab.run=ch22 で動く。トランザクションなし／ありで、2本のINSERTの間の例外がどう残るかを観察する。 */
@Component
@ConditionalOnProperty(name = "lab.run", havingValue = "ch22")
public class Ch22Runner implements ApplicationRunner {

    private final PollCreator creator;
    private final LabRepository repo;

    public Ch22Runner(PollCreator creator, LabRepository repo) {
        this.creator = creator;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        long host = repo.insertVisitor();
        report("開始前");

        System.out.println("=== @Transactional なし: 2本のINSERTの間で例外 ===");
        try {
            creator.createWithoutTx("トランザクションなし", "AAAAAAA2", host, true);
        } catch (IllegalStateException e) {
            System.out.println("例外: " + e.getMessage());
        }
        report("なしで失敗した後");

        System.out.println("=== @Transactional あり: 2本のINSERTの間で例外 ===");
        try {
            creator.createWithTx("トランザクションあり", "AAAAAAA3", host, true);
        } catch (IllegalStateException e) {
            System.out.println("例外: " + e.getMessage());
        }
        report("ありで失敗した後");

        System.out.println("=== @Transactional あり: 例外なし ===");
        long id = creator.createWithTx("トランザクションあり(成功)", "AAAAAAA4", host, false);
        System.out.println("作成できた poll id = " + id);
        report("成功した後");
    }

    private void report(String when) {
        System.out.printf("[%s] polls=%d participants=%d%n", when, repo.countRows("polls"),
                repo.countRows("participants"));
    }
}
