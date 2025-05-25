package org.threadPool;

import java.util.concurrent.Executor;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

// Интерфейс пользовательского пула потоков, для совместимости с Executor и поддержки submit().
public interface CustomExecutor extends Executor {

    // Метод берёт Runnable-команду и помещает её на выполнение в наш пул (часть интерфейса Executor)
    @Override
    void execute(Runnable command);

    // Добавляем метод submit, чтобы отправлять задачи, возвращающие значение.
    // Возвращаем Future<T>, чтобы можно было:
    // - дождаться результата через future.get()
    // - отменить задачу через future.cancel()
    // - проверить состояние через future.isDone()/isCancelled()
    <T> Future<T> submit(Callable<T> callable);

    // Метод для закрытия пула: больше не принимает новые задачи, но дожидается завершения уже принятых
    void shutdown();

    // Метод, который принудительно останавливает пул
    void shutdownNow();
}
