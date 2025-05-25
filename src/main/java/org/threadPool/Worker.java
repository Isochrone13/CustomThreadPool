package org.threadPool;

import java.util.concurrent.BlockingQueue;

public class Worker implements Runnable {
    // Очередь задач, из которой воркер будет «доставать» Runnable для выполнения
    private final BlockingQueue<Runnable> taskQueue;
    // Ссылка на наш пул,
    private final CustomThreadPoolExecutor pool;

    // Конструктор - принимаем свою очередь и пул, сохраняем их для работы в run().
    public Worker(BlockingQueue<Runnable> queue, CustomThreadPoolExecutor pool) {
        this.taskQueue = queue;  // сохраняем очередь для опроса
        this.pool = pool;        // сохраняем ссылку на пул для взаимодействия
    }

    // Точка входа» в поток. Ожидаем задачи с таймаутом keepAliveTime иЕсли получили задачу — выполняем её
    // Если нет задачи более keepAliveTime — проверяем, стоит ли завершиться
    // При shutdown завершаемся, когда очередь пуста или по прерыванию
    // В finally всегда уведомляем пул о своём завершении
    @Override
    public void run() {
        // Получаем имя текущего потока для более читаемых логов
        String name = Thread.currentThread().getName();
        // Сразу после старта воркер сообщает что свободен для выполнения задач
        pool.incrementIdleCount();
        try {
            while (true) {
                // 1) Если пул в состоянии shutdown и очередь пустая — выходим
                if (pool.isShutdown() && taskQueue.isEmpty()) {
                    break;
                }
                // 2) Ожидаем новую задачу, но не бесконечно, а только keepAliveTime
                Runnable task = taskQueue.poll(pool.getKeepAliveTime(), pool.getTimeUnit());
                if (task == null) {
                    // Таймаут ожидания истёк — нет новых задач за keepAliveTime
                    // Если пул закрывается или поток сверх corePoolSize — завершаемся
                    if (pool.isShutdown() || pool.getPoolSize() > pool.getCorePoolSize()) {
                        System.out.println("[Worker] " + name + " idle timeout, stopping.");
                        break;
                    } else {
                        // Иначе продолжаем ждать дальше, не завершаясь
                        continue;
                    }
                }
                // 3) Задача получена, снимаем отметку о свободном потоке
                pool.decrementIdleCount();
                // Проверяем снова флаг shutdown — если пул уже закрыт, выходим
                if (pool.isShutdown()) {
                    break;
                }
                // 4) Логируем начало выполнения задачи
                System.out.println("[Worker] " + name + " executes task");
                try {
                    // Запускаем саму Runnable-задачу (task.run())
                    task.run();
                } catch (Exception e) {
                    // Любая ошибка в задаче не должна убить воркер — логируем
                    e.printStackTrace();
                }
                // 5) По окончании возвращаем поток в разряд свободных
                pool.incrementIdleCount();
            }
        } catch (InterruptedException e) {
            // 6) Мы можем получить InterruptedException при shutdownNow()
            //    В таком случае просто выйдем из цикла и завершим run().
        } finally {
            // 7) В блоке finally гарантированно сообщаем пулу, что воркер завершен
            System.out.println("[Worker] " + name + " terminated.");
            pool.workerTerminated(this);
        }
    }
}
