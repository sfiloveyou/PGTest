package com.sf.pg;

import io.mycat.backend.postgresql.utils.PIOUtils;
import io.mycat.backend.postgresql.utils.PacketUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PGProxyServer2 {
	
	public final static Charset UTF8 = Charset.forName("utf-8");
	public final static int HEADER_LENGTH = 1;
	public final static int MSG_LENGTH = 4;
	public final static int MSG_DETAIL_LENGTH_EXCLUDE = HEADER_LENGTH+MSG_LENGTH+1;//with 1 end char
	public final static int BUFFER_ALLOC_SIZE = 1024*10;//with 1 end char
	
	public final static String[] PROXY_HOST = new String[]{"192.168.56.241","192.168.56.242"};
	public final static int PROXY_PORT = 5432;
	public final static String PROXY_DB = "postgres";
	public final static String PROXY_USER = "postgres";
	public final static String PROXY_PASSWORD = "123456";
	
	public final static String SERVER = "127.0.0.1";
	public final static int SERVER_PORT = 54320;
	private static Selector selector;
	private static Selector readSelector;
	private static SocketChannel OUTCHANNEL;
	private static BlockingQueue<Byte> SERVERQUEUE = new LinkedBlockingQueue<Byte>();
	private static BlockingQueue<Byte> CLIENTQUEUE = new LinkedBlockingQueue<Byte>();
	public  static void main(String[] args) {
		new Thread(new Runnable(){
			public void run() {
				try{
					ServerSocketChannel serverChannel = ServerSocketChannel.open();
					serverChannel.configureBlocking(false);
					serverChannel.socket().bind(new InetSocketAddress(SERVER_PORT));
					selector = Selector.open();
					serverChannel.register(selector, SelectionKey.OP_ACCEPT);
					System.out.println("server started");
					while (true) {
						selector.select(20);
						Iterator<?> iterator = selector.selectedKeys().iterator();
						while (iterator.hasNext()) {
							SelectionKey key = (SelectionKey) iterator.next();
							iterator.remove();
							serverHandler(key);
						}
					}
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}).start();
		for (int i = 1; i < PROXY_HOST.length; i++) {
			String client = PROXY_HOST[1];
			new Thread(new Runnable(){
				public void run() {
					try{
						SocketChannel socketChannel = SocketChannel.open();  
			            socketChannel.configureBlocking(false);
			            socketChannel.connect(new InetSocketAddress(client,PROXY_PORT));
			            socketChannel.register(readSelector, SelectionKey.OP_CONNECT);
			            while (true) {
			            	readSelector.select(20);
							Iterator<?> iterator = readSelector.selectedKeys().iterator();
							while (iterator.hasNext()) {
								SelectionKey key = (SelectionKey) iterator.next();
								iterator.remove();
								clientHandler(key);
							}
						}
					}catch(IOException e){
						e.printStackTrace();
					}
				}
			}).start();
		}
		new Thread(new Runnable(){
			public void run() {
				try{
					Byte b = null;
					while((b =SERVERQUEUE.take())!=null){
						ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_LENGTH);
						headerBuf.put(b);
						ByteBuffer lengthBuf = ByteBuffer.allocate(MSG_LENGTH);
						if(String.valueOf(b).equals("0")){
							lengthBuf.put(b);
							lengthBuf.put(SERVERQUEUE.take());
							lengthBuf.put(SERVERQUEUE.take());
							lengthBuf.put(SERVERQUEUE.take());
							int length = PIOUtils.redInteger4(lengthBuf, 0); 
							ByteBuffer msgBuf = ByteBuffer.allocate(length-4);
							for (int i = 0; i < length-4; i++) {
								msgBuf.put(SERVERQUEUE.take());
							}
							msgBuf.flip();
							System.out.println(PIOUtils.redString(msgBuf, 0,msgBuf.limit(), UTF8));
						}else{
							lengthBuf.put(SERVERQUEUE.take());
							lengthBuf.put(SERVERQUEUE.take());
							lengthBuf.put(SERVERQUEUE.take());
							lengthBuf.put(SERVERQUEUE.take());
							int length = PIOUtils.redInteger4(lengthBuf, 0); 
							int capacity = length-MSG_DETAIL_LENGTH_EXCLUDE;
							ByteBuffer msgBuf = ByteBuffer.allocate(capacity);
							for (int i = 0; i < capacity; i++) {
								msgBuf.put(SERVERQUEUE.take());
							}
							System.out.println(PIOUtils.redString(msgBuf, 0,msgBuf.limit(), UTF8));
						}
					}
					
				}catch (InterruptedException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();

	}
    protected static void serverHandler(SelectionKey key) throws IOException {
    	if (key.isAcceptable()) {
    		ServerSocketChannel server = (ServerSocketChannel) key.channel();
    		SocketChannel channel = server.accept();
    		channel.configureBlocking(false);
    		channel.register(selector, SelectionKey.OP_READ);
        }else if (key.isValid() && key.isReadable()) {
        	SocketChannel in = (SocketChannel) key.channel();
            try {
                ByteBuffer buffer = ByteBuffer.allocate(10*1024);
                readAll(key, buffer, in);
                for (int i = 0; i < buffer.limit(); i++) {
    				SERVERQUEUE.put(buffer.get(i));
    			}
            } catch (Exception e) {
                e.printStackTrace();
                cancelKey(key);
            } 
        }
	}
    
    protected static void clientHandler(SelectionKey key) throws IOException {
		SocketChannel in = (SocketChannel) key.channel();
    	if (key.isConnectable()) {
    		in.finishConnect();
    		key.interestOps(SelectionKey.OP_READ);
    		authClient(in);
        }else if (key.isValid() && key.isReadable()) {
        	ByteBuffer buffer = ByteBuffer.allocate(10*1024);
            readAll(key, buffer, in);
            //write(buffer, out);
        }
	}
    
    private static void authClient(SocketChannel sc) throws IOException {
		ByteBuffer buf = PacketUtils.makeStartUpPacket(PROXY_USER, PROXY_DB);
		buf.flip();
		buf.remaining();
		while(buf.hasRemaining()) {
			sc.write(buf);
		}
	}
    
	private static SocketChannel getOutChannel() throws IOException {
		//if(out==null){
			String client = PROXY_HOST[0];
			OUTCHANNEL = SocketChannel.open();  
			OUTCHANNEL.configureBlocking(false);
			OUTCHANNEL.connect(new InetSocketAddress(client, PROXY_PORT));	
		//}
		return OUTCHANNEL;
	}
	
	private static void readAll(SelectionKey key,ByteBuffer buffer,SocketChannel in)
			throws IOException {
		int count;
		while((count = in.read(buffer)) > 0) {
        }
        if (count == -1) {
        	cancelKey(key);
        }
	}
	
	private static void write(ByteBuffer buffer,SocketChannel out)
			throws IOException, InterruptedException {
		buffer.flip();
		while(!out.finishConnect()) {
		}
		while(buffer.hasRemaining()) {
			out.write(buffer);
		}
		if(out==OUTCHANNEL){
	    	for (int i = 0; i < buffer.limit(); i++) {
				SERVERQUEUE.put(buffer.get(i));
			}
    	}
	}
	
	private static void cancelKey(SelectionKey key) {
        key.cancel();
        SocketChannel out = (SocketChannel) key.attachment();
    	SelectionKey mappedKey = out.keyFor(key.selector());
        if (mappedKey != null) {
            mappedKey.cancel();
        }
    }
}
