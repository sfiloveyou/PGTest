package com.sf.pg.mapper;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created with IntelliJ IDEA.
 * User: chervanev
 * Date: 17.01.13
 * Time: 16:43
 * Упрощенный пул потоков-обработчиков
 */
public class WorkerPool {

    ArrayList<Thread> threads;

    public WorkerPool(Selector selector, ReentrantLock selectorGuard, int size) {
        threads = new ArrayList<Thread>(size);
        for(int i = 0; i < size; i++) {
            threads.add(new Thread(new Worker(selector, selectorGuard, new TransferHandler())));
        }
    }

    public void stop() {
        for(Thread thread : threads) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public void start() {
        for(Thread thread : threads) {
            thread.start();
        }
    }
}
