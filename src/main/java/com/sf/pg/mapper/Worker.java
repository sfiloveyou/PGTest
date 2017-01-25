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
 * 袣谢邪褋�? 芯斜褉邪斜芯褌褔懈泻 芯褔械褉械写�? SelectionKey 褔械褉械�? IHandler
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
                // 邪泻褑械锌褌芯褉 屑芯卸械�? �? 锌褉懈芯褉懈褌械褌芯�? 斜谢芯泻懈褉芯胁邪褌�? 褋械谢械泻褌芯褉
//                selectorGuard.lock();
//                selectorGuard.unlock();

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
