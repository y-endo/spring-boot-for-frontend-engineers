import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 複数の psql セッション（A, B, C）を、台本どおりの順序で動かし、出力を到着順に並べて表示する。
 * 「ターミナルを2つ開いて docker exec -it で psql に入り、交互に打つ」ことを、非対話で再現する道具。
 *
 * 使い方: java -Dstdout.encoding=UTF-8 scripts/TwoSessions.java scenarios/xxx.txt [コンテナ名]
 *
 * 台本の書式:
 *   A> SQL          セッションAに1行送る（送った後、0.4秒待ってから次の行へ）
 *   wait 2000       指定ミリ秒だけ待つ（ロック待ちが続く時間を作る）
 *   # コメント      無視
 */
void main(String[] args) throws Exception {
    Path scenario = Path.of(args[0]);
    String container = args.length > 1 ? args[1] : "part04-lab-pg";
    long t0 = System.nanoTime();

    record Line(long nanos, String session, String text) {
    }
    List<Line> lines = Collections.synchronizedList(new ArrayList<>());
    Map<String, Process> processes = new LinkedHashMap<>();
    Map<String, BufferedWriter> writers = new LinkedHashMap<>();
    List<Thread> readers = new ArrayList<>();

    for (String step : Files.readAllLines(scenario, StandardCharsets.UTF_8)) {
        if (step.length() > 2 && step.charAt(1) == '>' && !processes.containsKey(step.substring(0, 1))) {
            String name = step.substring(0, 1);
            Process p = new ProcessBuilder("docker", "exec", "-i", container, "psql", "-U", "lab", "-d", "lab",
                    "-X", "-a", "-P", "pager=off", "-v", "PROMPT1=", "-v", "VERBOSITY=terse")
                    .redirectErrorStream(true).start();
            processes.put(name, p);
            writers.put(name, new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)));
            Thread reader = Thread.ofPlatform().start(() -> {
                try (var in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = in.readLine()) != null) {
                        lines.add(new Line(System.nanoTime() - t0, name, l));
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            readers.add(reader);
        }
    }

    for (String step : Files.readAllLines(scenario, StandardCharsets.UTF_8)) {
        if (step.isBlank() || step.startsWith("#")) {
            continue;
        }
        if (step.startsWith("wait ")) {
            Thread.sleep(Long.parseLong(step.substring(5).trim()));
            continue;
        }
        String name = step.substring(0, 1);
        var w = writers.get(name);
        w.write(step.substring(3));
        w.newLine();
        w.flush();
        Thread.sleep(400);
    }

    Thread.sleep(500);
    for (var w : writers.values()) {
        w.write("\\q");
        w.newLine();
        w.flush();
    }
    for (var p : processes.values()) {
        p.waitFor();
    }
    for (var r : readers) {
        r.join();
    }

    List<Line> sorted = new ArrayList<>(lines);
    sorted.sort((a, b) -> Long.compare(a.nanos(), b.nanos()));
    for (Line l : sorted) {
        if (l.text().isBlank()) {
            continue;
        }
        System.out.printf("%4.1fs %s| %s%n", l.nanos() / 1e9, l.session(), l.text());
    }
}
