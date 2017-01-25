package com.sf.pg.mapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Created with IntelliJ IDEA.
 * User: chervanev
 * Date: 17.01.13
 * Time: 17:20
 * Работа по перемещению данных из одного канала в другой
 */
public class TransferHandler implements IHandler {
	public final static Charset UTF8 = Charset.forName("utf-8");

    public TransferHandler() {
    }

    @Override
    public boolean canHandle(SelectionKey selectionKey) {
        return selectionKey.isValid() && selectionKey.isReadable();
    }

    @Override
    public void perform(SelectionKey selectionKey) {
        SocketChannel channelIn = (SocketChannel) selectionKey.channel();
        SocketChannel channelOut = (SocketChannel) selectionKey.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(100 * 1024);
        int size = 0;
        try {
            while(!channelOut.finishConnect() ){
                Thread.sleep(25);
            }

            int count;
            while((count = channelIn.read(buffer)) > 0) {
                buffer.flip();
                size += buffer.remaining();
                while(buffer.hasRemaining()) {
                    channelOut.write(buffer);
                }
            }
            if (count == -1) {
                // EOF
                cancelKey(selectionKey);
                size = -1;
            }

        } catch (IOException e) {
            e.printStackTrace();
            cancelKey(selectionKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Transferred byte(s) " + size);

    }

    private void cancelKey(SelectionKey selectionKey) {
        selectionKey.cancel();
        SocketChannel mappedChannel = (SocketChannel) selectionKey.attachment();
        SelectionKey mappedKey = mappedChannel.keyFor(selectionKey.selector());
        if (mappedKey != null) {
            mappedKey.cancel();
            System.out.println("mappedKey.cancel");
        }
    }
}
