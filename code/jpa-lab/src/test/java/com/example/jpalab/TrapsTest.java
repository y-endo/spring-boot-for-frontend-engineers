package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import com.example.jpalab.poll.Option;
import com.example.jpalab.poll.OptionRepository;
import com.example.jpalab.poll.Poll;
import com.example.jpalab.poll.PollRepository;
import com.example.jpalab.support.AbstractLabTest;
import com.example.jpalab.traps.BadOption;
import com.example.jpalab.traps.BadPoll;
import com.example.jpalab.traps.BadPollRepository;
import com.example.jpalab.traps.BadPollService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 第41章の演習: AI が書きそうなコードの5つの罠を、テストで検出し、直したコードと比べる。 */
@AutoConfigureMockMvc
class TrapsTest extends AbstractLabTest {

    @Autowired
    BadPollService bad;

    @Autowired
    BadPollRepository badPolls;

    @Autowired
    PollRepository polls;

    @Autowired
    OptionRepository options;

    @Autowired
    MockMvcTester mvc;

    // ------------------------------------------------------------------ 罠1: EAGER

    @Test
    void 罠1_EAGERだと_一覧を取るだけで_1プラスN本() {
        seed(100, 3, 0);

        Measured<List<BadPoll>> badResult = measure(bad::findAll);
        Measured<List<Poll>> goodResult = measure(() -> polls.findAllByOrderByIdAsc());

        System.out.println("[罠1] 罠入り(EAGER): " + badResult.sqlCount() + "本 / 修正後(LAZY): " + goodResult.sqlCount() + "本");
        assertThat(badResult.result()).hasSize(100);
        assertThat(badResult.sqlCount()).isEqualTo(101);
        assertThat(goodResult.sqlCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 罠2: ループの中の findById

    @Test
    void 罠2_ループ内のfindByIdは_idの数だけSQLが出る(CapturedOutput output) {
        seed(20, 3, 0); // 20 件の投票（選択肢は各3件）。それぞれの最初の選択肢を指定する。
        List<Long> ids = LongStream.range(0, 20).map(i -> 1 + 3 * i).boxed().toList();
        mark(output);

        Measured<List<String>> badResult = measure(() -> bad.labelsOf(ids));
        List<String> badSql = sqlLines(output);
        mark(output);
        Measured<List<String>> goodResult = measure(() -> tx.execute(
                s -> options.findAllById(ids).stream().map(Option::getLabel).toList()));
        List<String> goodSql = sqlLines(output);

        System.out.println("[罠2] 罠入り(ループ内 findById): " + badResult.sqlCount() + "本 / 修正後(findAllById): "
                + goodResult.sqlCount() + "本");
        badSql.stream().limit(3).forEach(line -> System.out.println("  " + line));
        System.out.println("  ...");
        goodSql.forEach(line -> System.out.println("  " + line));
        assertThat(badResult.result()).hasSize(20);
        assertThat(goodResult.result()).hasSize(20);
        assertThat(badResult.sqlCount()).isEqualTo(40); // 1つのidごとに2本（選択肢＋poll の JOIN、poll の options の EAGER 読み込み）
        assertThat(goodResult.sqlCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 罠3: Entity をそのまま返す Controller

    @Test
    void 罠3_Entityを返すと_内部の列まで外に出る() throws Exception {
        seed(2, 2, 0);

        var result = mvc.get().uri("/api/bad/polls").exchange();

        System.out.println("[罠3] 罠入りの応答: " + result.getResponse().getContentAsString());
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[0].version").isEqualTo(0); // 楽観ロック用の内部の列
        assertThat(result).bodyJson().extractingPath("$[0].options").asArray().hasSize(2);

        var fixed = mvc.get().uri("/api/lab/polls/1").exchange();
        System.out.println("[罠3] 修正後(DTO)の応答: " + fixed.getResponse().getContentAsString());
        assertThat(fixed).bodyJson().doesNotHavePath("$.version");
    }

    // ------------------------------------------------------------------ 罠4: toString の循環

    @Test
    void 罠4_toStringが循環して_StackOverflowError() {
        BadPoll poll = new BadPoll("昼食は何がいい？", Instant.now());
        poll.addOption("パン");

        assertThatThrownBy(poll::toString).isInstanceOf(StackOverflowError.class);
        System.out.println("[罠4] poll.toString() → StackOverflowError");

        Poll fixed = new Poll("昼食は何がいい？", Instant.now());
        fixed.addOption("パン");
        System.out.println("[罠4] 修正後: " + fixed + " / " + fixed.getOptions());
        assertThat(fixed.toString()).startsWith("Poll{id=null");
    }

    @Test
    void 罠4_ログに出そうとすると_toStringの失敗が握りつぶされる(CapturedOutput output) {
        seed(1, 2, 0);
        mark(output);

        bad.close(1L, Instant.now());

        String line = output.getOut().lines().filter(l -> l.contains("closed:")).findFirst().orElseThrow();
        System.out.println("[罠4] ログ: " + line.substring(line.indexOf("closed:")));
    }

    // ------------------------------------------------------------------ 罠5: id だけの equals / hashCode

    @Test
    void 罠5_保存前のEntityはすべて等しく_Setで1件に潰れる(CapturedOutput output) {
        BadPoll poll = new BadPoll("昼食は何がいい？", Instant.now());
        poll.addOption("パン");
        poll.addOption("ごはん");
        poll.addOption("めん");
        System.out.println("[罠5] 3回 addOption したのに options の件数 = " + poll.getOptions().size());
        assertThat(poll.getOptions()).hasSize(1);

        Poll fixed = new Poll("昼食は何がいい？", Instant.now());
        Set<Option> set = new HashSet<>();
        set.add(fixed.addOption("パン"));
        set.add(fixed.addOption("ごはん"));
        set.add(fixed.addOption("めん"));
        System.out.println("[罠5] 修正後の equals/hashCode で Set に入れた件数 = " + set.size());
        assertThat(set).hasSize(3);
    }

    @Test
    void 罠5_保存するとhashCodeが変わり_HashSetから見つからなくなる() {
        BadPoll poll = new BadPoll("昼食は何がいい？", Instant.now());
        poll.addOption("パン");
        BadOption option = poll.getOptions().iterator().next();
        int hashBefore = option.hashCode();

        tx.executeWithoutResult(s -> badPolls.save(poll)); // IDENTITY で id が採番される

        System.out.println("[罠5] 保存前 hashCode=" + hashBefore + " → 保存後 hashCode=" + option.hashCode()
                + " / poll.getOptions().contains(option) = " + poll.getOptions().contains(option));
        assertThat(option.hashCode()).isNotEqualTo(hashBefore);
        assertThat(poll.getOptions().contains(option)).isFalse();
    }
}
