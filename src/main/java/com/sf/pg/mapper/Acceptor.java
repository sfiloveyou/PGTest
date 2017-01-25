package com.sf.pg.mapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created with IntelliJ IDEA.
 * User: chervanev
 * Date: 17.01.13
 * Time: 16:48
 * Акцептор новых соединений
 * Выполняется в главном потоке
 * Умеет снимать блокировку вспомогательных процессов с селектора
 */
public class Acceptor {
    final private Selector acceptSelector;
    final private Selector readSelector;
    final private ReentrantLock selectorGuard;
    private WorkerPool pool;
    private HashMap<ServerSocketChannel, InetSocketAddress> mapping;

    public Acceptor(int threadCount) throws IOException {
        acceptSelector = Selector.open();
        readSelector = Selector.open();
        mapping = new HashMap<ServerSocketChannel, InetSocketAddress>();
        selectorGuard = new ReentrantLock();
        this.pool = new WorkerPool(readSelector, selectorGuard, threadCount);
    }

    public void addMapping(MappingInfo mappingInfo) throws IOException {
        addMapping(mappingInfo.localPort(), mappingInfo.remoteHost(), mappingInfo.remotePort());
    }

    public void addMapping(int localPort, String remoteHost, int remotePort) throws IOException {
        ServerSocketChannel ssk = ServerSocketChannel.open();
        mapping.put(ssk, new InetSocketAddress(remoteHost, remotePort));
        ssk.configureBlocking(false);
        ssk.bind(new InetSocketAddress(localPort));
        ssk.register(acceptSelector, SelectionKey.OP_ACCEPT);
    }

    public void run() {
        pool.start();
        while(!Thread.currentThread().isInterrupted()) {
            if (!tryAccept())
                continue;

            for(SelectionKey selectionKey : acceptSelector.selectedKeys()) {
                if (selectionKey.isAcceptable()) {
                    dispatch((ServerSocketChannel) selectionKey.channel());
                }
            }

            acceptSelector.selectedKeys().clear();
        }
        pool.stop();
    }

    private void dispatch(ServerSocketChannel server) {
        SocketChannel clientChannel = null;
        try {
            clientChannel = server.accept();
            clientChannel.configureBlocking(false);
            try {
                SocketChannel mappedChannel = outerChannelFromSocket(server);
                System.out.println(Thread.currentThread().getName() + " try to register...");
                selectorGuard.lock();
                try {
                    synchronized (readSelector.wakeup())
                    {
                        System.out.println(Thread.currentThread().getName() + " sync-in");
                        clientChannel.register(readSelector, SelectionKey.OP_READ, mappedChannel);
                        mappedChannel.register(readSelector, SelectionKey.OP_READ, clientChannel);
                    }
                    System.out.println("Client registered");
                } finally {
                    selectorGuard.unlock();
                }
            } catch (ClosedChannelException e) {
                // nothing
            }

        } catch (Exception e) {
            // accept failed
            e.printStackTrace();
            if (clientChannel != null && clientChannel.isConnected()) {
                try {
                    clientChannel.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private boolean tryAccept() {
        try {
            return acceptSelector.select() != 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**@return "внешний" канал для записи, в зависимости от сокета, принявшего соединение */
    private SocketChannel outerChannelFromSocket(ServerSocketChannel ssc) throws IOException {
        SocketChannel result = SocketChannel.open();
        result.configureBlocking(false);
        result.connect(mapping.get(ssc));
        return result;
    }

    public int mappingCount() {
        return mapping.size();
    }
}
