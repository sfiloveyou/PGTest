package com.sf.pg;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class PGProxyServer3 {
	public final static Charset UTF8 = Charset.forName("utf-8");
	public final static int HEADER_LENGTH = 1;
	public final static int MSG_LENGTH = 4;
	public final static int MSG_DETAIL_LENGTH_EXCLUDE = HEADER_LENGTH+MSG_LENGTH-1;//with 1 end char
	public final static int BUFFER_ALLOC_SIZE = 1024*10;//with 1 end char
	
	public final static String[] PROXY_HOST = new String[]{"192.168.56.241","192.168.56.242"};
	public final static int PROXY_PORT = 5432;
	public final static String PROXY_DB = "postgres";
	public final static String PROXY_USER = "postgres";
	public final static String PROXY_PASSWORD = "123456";
	
	public final static String SERVER = "127.0.0.1";
	public final static int SERVER_PORT = 54320;
	private static Selector selector;
	public  static void main(String[] args) {
		new Thread(new Runnable(){
			public void run() {
				try{
					selector = Selector.open();
					ServerSocketChannel serverChannel = ServerSocketChannel.open();
					serverChannel.configureBlocking(false);
					serverChannel.socket().bind(new InetSocketAddress(SERVER_PORT));
					serverChannel.register(selector, SelectionKey.OP_ACCEPT);
					System.out.println("server started");
					while (true) {
						selector.select(20);
						Set<SelectionKey> selectedKeys = selector.selectedKeys();
						Iterator<?> iterator = selectedKeys.iterator();
						while (iterator.hasNext()) {
							SelectionKey key = (SelectionKey) iterator.next();
							iterator.remove();
							serverHandler(key);
						}
						selectedKeys.clear();
					}
					
				}catch(IOException e){
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
    		channel.register(selector, SelectionKey.OP_READ,null);
        }else if (key.isValid() && key.isReadable()) {
        	SocketChannel in = (SocketChannel) key.channel();
            try {
            	ByteBuffer buffer = ByteBuffer.allocate(BUFFER_ALLOC_SIZE);
                read(key, buffer, in);
                
                SocketChannel out = (SocketChannel) key.attachment();
            	if(out == null || !out.isConnected()){
            		String[] address = in.getRemoteAddress().toString().split(":");
            		int port = Integer.parseInt(address[address.length-1]);
            		System.out.println("port::"+port);
            		String client = null;
            		if((port%2)==0){
                    	client = PROXY_HOST[1];
                    }else{
                    	client = PROXY_HOST[0];
                    }
            		out = SocketChannel.open();  
                    out.configureBlocking(false);
                    out.connect(new InetSocketAddress(client,PROXY_PORT));
                    out.register(selector, SelectionKey.OP_READ,in);
                    in.register(selector, SelectionKey.OP_READ,out);
                    while(!out.finishConnect()){
                    }
            	}
                write(buffer, out);
            } catch (Exception e) {
                e.printStackTrace();
                cancelKey(key);
            } 
        }
	}
	
	private static int read(SelectionKey key,ByteBuffer buffer,SocketChannel in)
			throws IOException {
		int count;
		while((count = in.read(buffer)) > 0) {
        }
        if (count == -1) {
        	cancelKey(key);
        }
        return count;
	}
	
	private static void write(ByteBuffer buffer,SocketChannel out)
			throws IOException {
		buffer.flip();
		while(buffer.hasRemaining()) {
			out.write(buffer);
		}
	}
	
	private static void cancelKey(SelectionKey key) throws IOException {
		System.out.println("cancelKey");
        key.cancel();
        SocketChannel out = (SocketChannel) key.attachment();
    	SelectionKey mappedKey = out.keyFor(key.selector());
        if (mappedKey != null) {
            mappedKey.cancel();
        }
        SocketChannel in = (SocketChannel) key.channel();
        in.close();
    }
}
