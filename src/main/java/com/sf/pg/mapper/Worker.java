package com.sf.pg.mapper;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created with IntelliJ IDEA.
 * User: chervanev
 * Date: 17.01.13
 * Time: 16:39
 * Класс обработчик очереди SelectionKey через IHandler
 */
public class Worker implements Runnable{
    final Selector selector;
    final ReentrantLock selectorGuard;
    IHandler handler;

    public Worker(Selector selector, ReentrantLock selectorGuard, IHandler handler) {
        this.selectorGuard = selectorGuard;
        this.selector = selector;
        this.handler = handler;
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                SelectionKey key;
                // акцептор может с приоритетом блокировать селектор
                

                synchronized (selector)
                {
                    if (selectorGuard.isLocked()) {
                        System.out.println(Thread.currentThread().getName() + ": unlock now");
                        continue;
                    }

                    System.out.println(Thread.currentThread().getName() + ": get keys...");
                    if (selector.selectedKeys().size() == 0 && selector.select() == 0 ) {
                        System.out.println(Thread.currentThread().getName() + ": no keys");
                        continue;
                    }
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    key = iterator.next();
                    iterator.remove();
                    System.out.println(Thread.currentThread().getName() + " try perform...");
                    if (handler.canHandle(key)) {
                        handler.perform(key);
                    }
                }

            } catch (IOException e) {
                // nothing
            }
        }

    }
}
