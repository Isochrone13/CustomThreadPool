package org.threadPool;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class CustomRejectedExecutionHandler implements RejectedExecutionHandler {

    // Этот метод вызывается, когда executor не может принять новую задачу:
    // - все потоки уже заняты (poolSize == maxPoolSize)
    // - и очереди для задач тоже заполнены (offer() вернул false)
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

        // Логируем перегрузку пула
        System.out.println(
                "[Rejected] Task was rejected due to overload! Executing in caller thread."
        );

        // Вместо того чтобы просто отбросить задачу, мы запускаем её в том же** потоке
        r.run();
    }
}
