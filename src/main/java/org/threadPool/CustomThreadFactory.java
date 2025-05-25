package org.threadPool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {
    private final AtomicInteger threadCount = new AtomicInteger(1);
    // Префикс, который сформируется из имени пула
    private final String namePrefix;

    // Конструктор: при создании фабрики указываем имя пула
    public CustomThreadFactory(String poolName) {
        this.namePrefix = poolName + "-worker-";
    }

    // Вызываем каждый раз, когда пул хочет завести новый поток
    @Override
    public Thread newThread(Runnable r) {

        // Получаем текущее значение счётчика и сразу его инкрементируем
        String threadName = namePrefix + threadCount.getAndIncrement();

        // Логируем факт создания потока
        System.out.println("[ThreadFactory] Creating new thread: " + threadName);

        // Передаём наш Runnable (Worker) и красивое имя в конструктор Thread
        Thread t = new Thread(r, threadName);

        // Делаем поток не демонским и JVM будет дожидаться его завершения при выходе из main(),
        t.setDaemon(false);

        return t;
    }
}

