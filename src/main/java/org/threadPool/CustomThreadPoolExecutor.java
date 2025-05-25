package org.threadPool;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPoolExecutor implements CustomExecutor {
    // corePoolSize — минимальное число потоков, которые всегда должны быть запущены
    private final int corePoolSize;
    // maxPoolSize — максимально допустимое число потоков при пиковой нагрузке
    private final int maxPoolSize;
    // keepAliveTime + timeUnit — время ожидания задачи перед тем, как лишний поток завершится
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    // minSpareThreads — минимальный резерв свободных потоков
    private final int minSpareThreads;

    // Список очередей задач: по одной на каждый потенциальный поток
    private final List<BlockingQueue<Runnable>> queues = new ArrayList<>();

    // Счётчик для round-robin распределения задач между очередями
    private final AtomicInteger nextQueue = new AtomicInteger(0);
    // Текущее число запущенных потоков
    private final AtomicInteger poolSize = new AtomicInteger(0);
    // Сколько потоков сейчас свободны и ждут задач
    private final AtomicInteger idleCount = new AtomicInteger(0);

    // Множество активных Worker-объектов для управления ими
    private final Set<Worker> workers = ConcurrentHashMap.<Worker>newKeySet();

    // Фабрика потоков — мы получаем ThreadFactory извне, чтобы настраивать имена, флаги и т.п.
    private final ThreadFactory threadFactory;
    // Обработчик отказов — как реагировать, если очередь переполнена
    private final RejectedExecutionHandler rejectedHandler;

    // Флаг, что к пулу был применён shutdown — больше не принимаем новые задачи
    private volatile boolean shutdown = false;

    public CustomThreadPoolExecutor(int corePoolSize, int maxPoolSize,
                                    long keepAliveTime, TimeUnit timeUnit,
                                    int queueSize, int minSpareThreads,
                                    ThreadFactory threadFactory,
                                    RejectedExecutionHandler rejectedHandler) {
        this.corePoolSize       = corePoolSize;
        this.maxPoolSize        = maxPoolSize;
        this.keepAliveTime      = keepAliveTime;
        this.timeUnit           = timeUnit;
        this.minSpareThreads    = minSpareThreads;
        this.threadFactory      = threadFactory;
        this.rejectedHandler    = rejectedHandler;

        // 1) Заранее выделяем «место» под N воркеров и создаем maxPoolSize очередей, каждая с capacity = queueSize
        for (int i = 0; i < maxPoolSize; i++) {
            queues.add(new LinkedBlockingQueue<>(queueSize));
        }
        // 2) Запускаем corePoolSize воркеров сразу при старте
        for (int i = 0; i < corePoolSize; i++) {
            addWorker(i);
        }
    }

    // Создаем нового воркера, привязанного к очереди с индексом queueIndex
    private void addWorker(int queueIndex) {
        BlockingQueue<Runnable> queue = queues.get(queueIndex);

        // Worker — это Runnable, который в цикле берёт задачи из queue
        Worker worker = new Worker(queue, this);

        // Фабрика создаёт готовый объект Thread с нашим Runnable и кастомным именем
        Thread t = threadFactory.newThread(worker);

        // Увеличиваем число активных потоков перед запуском
        poolSize.incrementAndGet();

        // Сохраняем ссылку на воркера, чтобы при завершении корректно уменьшать poolSize
        workers.add(worker);

        // Запускаем поток
        t.start();
    }

    // Основной метод для добавления задачи в пул
    @Override
    public void execute(Runnable command) {
        // 1) Запрещаем новые задачи, если пул закрыт
        if (shutdown) {
            throw new RejectedExecutionException("Executor shutdown");
        }

        // 2) Вычисляем индекс очереди по Round-Robin
        int idx = nextQueue.getAndIncrement() % queues.size();
        if (idx < 0) { // на всякий случай, если nextQueue переполнится
            idx += queues.size();
        }
        BlockingQueue<Runnable> queue = queues.get(idx);

        // 3) Если ещё не набрали corePoolSize, создаём поток сразу в этой очереди
        if (poolSize.get() < corePoolSize) {
            addWorker(idx);
        }

        // 4) Снова вычисляем idx, чтобы не поставить все новые задачи в те же очереди,
        //    а продолжить круговой обход и при создании воркера
        idx = nextQueue.getAndIncrement() % queues.size();
        if (idx < 0) {
            idx += queues.size();
        }
        queue = queues.get(idx);

        // 5) Пытаемся вставить задачу в очередь
        if (queue.offer(command)) {
            // Успешно поставили — проверяем запас свободных воркеров
            if (idleCount.get() < minSpareThreads && poolSize.get() < maxPoolSize) {
                // создаём дополнительного воркера в следующей по кругу очереди
                int spareIdx = (idx + 1) % queues.size();
                addWorker(spareIdx);
            }
            return;
        }

        // 6) Если очередь переполнена, но можем расти — добавляем воркер в эту же очередь
        if (poolSize.get() < maxPoolSize) {
            addWorker(idx);
            // и повторно пробуем вставить задачу
            if (queue.offer(command)) {
                return;
            }
        }

        // 7) Иначе идёт отказ
        rejectedHandler.rejectedExecution(command, null);
    }

    // Создаём FutureTask и передаём его в execute() (обёртка для Callable)
    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> future = new FutureTask<>(callable);
        execute(future);
        return future;
    }

    // Больше не принимаем задачи, но даём воркерам добежать до пустых очередей.
    @Override
    public void shutdown() {
        shutdown = true;
        System.out.println("[Pool] Shutdown initiated.");
    }

    // Принудительная остановка
    @Override
    public void shutdownNow() {
        shutdown = true;
        System.out.println("[Pool] Force shutdown initiated.");
    }

    // Увеличиваем счётчик свободных потоков
    void incrementIdleCount() {
        idleCount.incrementAndGet();
    }

    // Уменьшаем счётчик свободных потоков
    void decrementIdleCount() {
        idleCount.decrementAndGet();
    }

    // Проверка: вызван ли shutdown
    boolean isShutdown() {
        return shutdown;
    }

    // Получить corePoolSize (нужно воркерам для логики завершения)
    int getCorePoolSize() {
        return corePoolSize;
    }

    // Текущее число потоков (используется для таймаута)
    int getPoolSize() {
        return poolSize.get();
    }

    // Получить время бездействия перед завершением
    long getKeepAliveTime() {
        return keepAliveTime;
    }

    // Получить единицу измерения keepAliveTime
    TimeUnit getTimeUnit() {
        return timeUnit;
    }

    // Когда воркер умирает убираем его из множества workers и уменьшаем общее число потоков
    void workerTerminated(Worker w) {
        workers.remove(w);
        poolSize.decrementAndGet();
    }

    // Ожидаем завершение работы
    public void awaitTermination() {
        // Блокируемся, пока poolSize не станет 0
        while (poolSize.get() > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
