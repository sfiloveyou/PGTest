package com.sf.pg.mapper;
import io.mycat.backend.postgresql.utils.PIOUtils;

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
	public final static int HEADER_LENGTH = 1;
	public final static int MSG_LENGTH = 4;
	public final static int MSG_DETAIL_LENGTH_EXCLUDE = HEADER_LENGTH+MSG_LENGTH+1;//with 1 end char
	
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

        
        int count = 0;
        int size = 0;
        try {
            while(!channelOut.finishConnect() ){
                Thread.sleep(25);
            }
            ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_LENGTH);
            while(headerBuf.position() < HEADER_LENGTH) {
            	count =channelIn.read(headerBuf);
                if (count == -1) {
                    // EOF
                    cancelKey(selectionKey);
                    //size = -1;
                    return;
                }
            }
            headerBuf.flip();
            size +=channelOut.write(headerBuf);
            System.out.println("Transferred byte(s) " + count);
            String header = PIOUtils.redString(headerBuf,0,UTF8);
            System.out.println("header::"+header);
            ByteBuffer msgBuf = null;
            if(header!=null && "Q".equals(header.trim())){
                ByteBuffer lengthBuf = ByteBuffer.allocate(MSG_LENGTH);
                while(lengthBuf.position() < MSG_LENGTH) {
                	count =channelIn.read(lengthBuf);
                	if (count == -1) {
                        // EOF
                        cancelKey(selectionKey);
                        return;
                    }
                }
                lengthBuf.flip();
                size += channelOut.write(lengthBuf);
                System.out.println("Transferred byte(s) " + count);
                int length = PIOUtils.redInteger4(lengthBuf, 0);                
                System.out.println("length::"+length);
                
            	msgBuf = ByteBuffer.allocate(length-MSG_DETAIL_LENGTH_EXCLUDE);
                while(msgBuf.position() < length-MSG_DETAIL_LENGTH_EXCLUDE) {
                	count = channelIn.read(msgBuf);
                	if (count == -1) {
                        // EOF
                        cancelKey(selectionKey);
                        return;
                    }
                }         
            }else{
            	msgBuf = ByteBuffer.allocate(1024);
            	count = channelIn.read(msgBuf);
            	if (count == -1) {
                    // EOF
                    cancelKey(selectionKey);
                    return;
                }
            }
            msgBuf.flip();
            size += channelOut.write(msgBuf);
            System.out.println("Transferred byte(s) " + count);
            System.out.println("msg::"+PIOUtils.redString(msgBuf, 0,msgBuf.limit(), UTF8));
            if (count == -1) {
                // EOF
                cancelKey(selectionKey);
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
