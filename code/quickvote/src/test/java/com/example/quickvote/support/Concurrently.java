package com.example.quickvote.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * 「狙った競合」を作るための道具。
 * 全スレッドが準備できるまで待機させ、CountDownLatch で一斉に開始する。
 * 「100回動いて失敗しなかった」は根拠にならないので、同時に飛び込ませる回数ではなく、
 * 「必ず競合する形で開始する」ことと「結果の不変条件」で確かめる。
 */
public final class Concurrently {

    public record Outcome<T>(T value, Throwable error) {
        public boolean succeeded() {
            return error == null;
        }
    }

    private Concurrently() {
    }

    public static <T> List<Outcome<T>> run(int threads, IntFunction<Callable<T>> taskFactory) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<T> task = taskFactory.apply(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome<>(task.call(), null);
                    } catch (Throwable e) {
                        return new Outcome<>(null, e);
                    }
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("スレッドが揃いませんでした");
            }
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException(e);
        } finally {
            executor.shutdownNow();
        }
    }
}
