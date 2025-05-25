package org.threadPool;

import java.util.*;
import java.util.concurrent.*;

public class FixedPoolTest {
    private static final int NUM_TASKS = 10000;
    private static final int MIN_SLEEP_MS = 10;
    private static final int MAX_SLEEP_MS = 100;
    private static final long TIMEOUT_SEC = 60;

    public static void main(String[] args) throws InterruptedException {
        // 1) Настраиваем стандартный фиксированный пул из 10 потоков
        ExecutorService executor = Executors.newFixedThreadPool(10);

        System.out.println("=== FixedThreadPool(10) Test ===");

        runBenchmark(executor);
    }

    private static void runBenchmark(ExecutorService executor) throws InterruptedException {
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(NUM_TASKS);

        long startTime = System.nanoTime();

        // Запускаем NUM_TASKS задач подряд
        for (int i = 0; i < NUM_TASKS; i++) {
            // Помещаем в пул новую Runnable-задачу
            executor.submit(() -> {
                // Засекаем точное время начала выполнения этой задачи (в наносекундах)
                long t0 = System.nanoTime();
                try {
                    // Генерируем случайную задержку от MIN_SLEEP_MS до MAX_SLEEP_MS (включительно)
                    int sleep = ThreadLocalRandom.current()
                            .nextInt(MIN_SLEEP_MS, MAX_SLEEP_MS + 1);
                    // Приостанавливаем текущий поток на эту случайную величину
                    Thread.sleep(sleep);
                // Если поток был прерван (например, при shutdownNow()), просто продолжаем
                } catch (InterruptedException ignored) {}
                // Вычисляем фактическое время выполнения: разница между «сейчас» и t0, переводим в миллисекунды
                long dt = (System.nanoTime() - t0) / 1_000_000;
                // Сохраняем результат в общий потокобезопасный список задержек
                latencies.add(dt);
                // Уменьшаем счётчик latch — это сигнализирует главному потоку, что одна задача завершилась
                latch.countDown();
            });
        }

        // Ждём завершения либо таймаута
        boolean finished = latch.await(TIMEOUT_SEC, TimeUnit.SECONDS);
        long totalMs = (System.nanoTime() - startTime) / 1_000_000;

        // Завершаем
        if (!finished) {
            System.out.println("[WARN] Не все задачи завершились, вызываем shutdownNow()");
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }

        // Можно дождаться окончательного завершения, но latch уже отработал
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Сбор статистики
        double avg = latencies.stream().mapToLong(x -> x).average().orElse(0);
        long min = latencies.stream().mapToLong(x -> x).min().orElse(0);
        long max = latencies.stream().mapToLong(x -> x).max().orElse(0);
        double throughput = NUM_TASKS / (totalMs / 1000.0);

        // Вывод
        System.out.printf("Общее время: %d ms%n", totalMs);
        System.out.printf("Throughput: %.2f tasks/sec%n", throughput);
        System.out.printf("Латентность (ms): min=%d, avg=%.2f, max=%d%n", min, avg, max);
    }
}

