package com.sf.pg;

import io.mycat.backend.postgresql.utils.PIOUtils;
import io.mycat.backend.postgresql.utils.PacketUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PGConnect {
	private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(3, 5, 2000, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(20));
	public final static Charset UTF8 = Charset.forName("utf-8");
	public final static int HEADER_LENGTH = 1;
	public final static int MSG_LENGTH = 4;
	public final static int MSG_DETAIL_LENGTH_EXCLUDE = HEADER_LENGTH+MSG_LENGTH+1;//with 1 end char
	static boolean authed = false;
	public static void main(String[] args) {
		try {  
            SocketChannel socketChannel = SocketChannel.open();  
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress("192.168.56.241",5432));
            
            Selector selector = Selector.open();
            socketChannel.register(selector,SelectionKey.OP_CONNECT);
            //threadPool.execute(new Runnable() {
                //@Override
                //public void run() {
                    while (true) {
                        try {
                            if (selector.select(20) == 0) {
                                continue;
                            }
                            boolean authed = false;
                            int count = 0 ;
                            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                            while (iterator.hasNext()) {
                                SelectionKey selectionKey = iterator.next();
                                iterator.remove();
                                if (selectionKey.isConnectable()) {
                                    SocketChannel sc = (SocketChannel) selectionKey.channel();
                                    sc.finishConnect();
                                    //sc.register(selector, SelectionKey.OP_WRITE);
                                    selectionKey.interestOps(SelectionKey.OP_READ);
                                    if(!authed){
                                    	ByteBuffer buf = PacketUtils.makeStartUpPacket("postgres", "postgres");
                            			buf.flip();
                            			buf.remaining();
                                        while(buf.hasRemaining()) {
                                        	sc.write(buf);
                                        }
                                    }
                                } else if (selectionKey.isValid() && selectionKey.isWritable()) {
                                    System.out.println("isWritable");
                                }else if (selectionKey.isValid() && selectionKey.isReadable()) {
                                    SocketChannel sc = (SocketChannel) selectionKey.channel();
                                    ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_LENGTH);
                                    while(headerBuf.position() < HEADER_LENGTH) {
                                    	count =sc.read(headerBuf);
                                        if (count == -1) {
                                            // EOF
                                            cancelKey(selectionKey);
                                            //size = -1;
                                            return;
                                        }
                                    }
                                    System.out.println("Transferred byte(s) " + count);
                                    String header = PIOUtils.redString(headerBuf,0,UTF8);
                                    System.out.println("header::"+header);
                                    ByteBuffer msgBuf = null;
                                    if(header!=null && "R".equals(header.trim())){
                                        ByteBuffer lengthBuf = ByteBuffer.allocate(MSG_LENGTH);
                                        while(lengthBuf.position() < MSG_LENGTH) {
                                        	count =sc.read(lengthBuf);
                                        	if (count == -1) {
                                                // EOF
                                                cancelKey(selectionKey);
                                                return;
                                            }
                                        }
                                        System.out.println("Transferred byte(s) " + count);
                                        int length = PIOUtils.redInteger4(lengthBuf, 0);                
                                        System.out.println("length::"+length);
                                        
                                    	msgBuf = ByteBuffer.allocate(length-MSG_DETAIL_LENGTH_EXCLUDE);
                                        while(msgBuf.position() < length-MSG_DETAIL_LENGTH_EXCLUDE) {
                                        	count = sc.read(msgBuf);
                                        	if (count == -1) {
                                                // EOF
                                                cancelKey(selectionKey);
                                                return;
                                            }
                                        }  
                                        System.out.println("Transferred byte(s) " + count);
                                        int code = PIOUtils.redInteger2(msgBuf, 0);
										System.out.println("code::"+code);
										if(code==0){
											authed = true;
											ByteBuffer b = ByteBuffer.allocate(60);
											b.put("Q".getBytes());
											b.put(new byte[]{0,0,0,26});
											b.put("select * from t_user;".getBytes("UTF-8"));
											b.put(new byte[]{0});
											b.flip();
											while(b.hasRemaining()) {
												sc.write(b);
											}
										}
										
                                    }else{
                                    	msgBuf = ByteBuffer.allocate(10*1024);
                                    	while((count = sc.read(msgBuf)) == -1) {
                                            // EOF
                                            cancelKey(selectionKey);
                                        }
                                    	msgBuf.flip();
                                    	System.out.println("msg::"+PIOUtils.redString(msgBuf, 0,msgBuf.limit(), UTF8));
                                    }
                                }
                            }
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
//                }
//            });
        } catch (IOException e) {  
            e.printStackTrace();  
        }
	}
    private static void cancelKey(SelectionKey selectionKey) {
    	authed = false;
    	System.out.println("cancel");
        selectionKey.cancel();
//        SocketChannel mappedChannel = (SocketChannel) selectionKey.attachment();
//        SelectionKey mappedKey = mappedChannel.keyFor(selectionKey.selector());
//        if (mappedKey != null) {
//            mappedKey.cancel();
//            System.out.println("mappedKey.cancel");
//        }
    }
}
