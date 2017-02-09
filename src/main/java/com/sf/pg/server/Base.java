package com.sf.pg.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public abstract class Base {
	protected final static Charset UTF8 = Charset.forName("utf-8");
	protected final static int HEADER_LENGTH = 1;
	protected final static int MSG_LENGTH = 4;
	protected final static int MSG_DETAIL_LENGTH_EXCLUDE = HEADER_LENGTH+MSG_LENGTH-1;//with 1 end char
	protected final static int BUFFER_ALLOC_SIZE = 1024*10;//with 1 end char
	
	protected final static String[] PROXY_HOST = new String[]{"192.168.56.241","192.168.56.242"};
	protected final static int PROXY_PORT = 5432;
	protected final static String PROXY_DB = "postgres";
	protected final static String PROXY_USER = "postgres";
	protected final static String PROXY_PASSWORD = "123456";
	
	protected final static String SERVER = "127.0.0.1";
	protected final static int SERVER_PORT = 54320;
	protected Selector selector;
	protected final static long selector_time = 500L;
	
	protected int read(SelectionKey key,ByteBuffer buffer,SocketChannel in)
			throws IOException {
		int count;
		while((count = in.read(buffer)) > 0) {
        }
		//buffer.flip();
        if (count == -1) {
        	cancelKey(key);
        }
        return buffer.limit();
	}
	
	protected void write(ByteBuffer buffer,SocketChannel out)
			throws IOException {
		buffer.flip();
		while(buffer.hasRemaining()) {
			out.write(buffer);
		}
	}
	
	protected void cancelKey(SelectionKey key) throws IOException {
		System.out.println("cancelKey");
        key.cancel();
//        SocketChannel out = (SocketChannel) key.attachment();
//    	SelectionKey mappedKey = out.keyFor(key.selector());
//        if (mappedKey != null) {
//            mappedKey.cancel();
//        }
//        SocketChannel in = (SocketChannel) key.channel();
//        in.close();
    }
	
	void run(){
		new Thread(new Runnable(){
			public void run() {
				try{
					while (true) {
						selector.select(selector_time);
						Set<SelectionKey> selectedKeys = selector.selectedKeys();
						Iterator<?> iterator = selectedKeys.iterator();
						while (iterator.hasNext()) {
							SelectionKey key = (SelectionKey) iterator.next();
							iterator.remove();
							handler(key);
						}
						selectedKeys.clear();
					}
					
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}).start();
	}

	abstract void handler(SelectionKey key) throws IOException;
	
	public Selector getSelector() {
		return selector;
	}

	public void setSelector(Selector selector) {
		this.selector = selector;
	}
}
