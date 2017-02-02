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
import java.util.Random;

public class PGProxyServer {
	
	private static final Random RANDOM = new Random();
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
	
	public  static void main(String[] args) {
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
    protected static void serverHandler(SelectionKey key) throws IOException {
    	if (key.isAcceptable()) {
    		ServerSocketChannel server = (ServerSocketChannel) key.channel();
    		SocketChannel channel = server.accept();
    		channel.configureBlocking(false);
    		channel.register(selector, SelectionKey.OP_READ);
    		//System.out.println("isAcceptable");
			
        }else if (key.isConnectable()) {
        	SocketChannel in = (SocketChannel) key.channel();
    		in.finishConnect();
    		key.interestOps(SelectionKey.OP_READ);
        	authClient(in);
        	//System.out.println("isConnectable");
        }else if (key.isValid() && key.isReadable()) {
        	SocketChannel in = (SocketChannel) key.channel();
        	SocketChannel out = (SocketChannel) key.attachment();
            try {
                ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_LENGTH);
                read(key, headerBuf, in);
                String header = PIOUtils.redString(headerBuf,0,UTF8);
                ByteBuffer msgBuf = null;
                ByteBuffer lengthBuf = ByteBuffer.allocate(MSG_LENGTH);
                if(header!=null && "Q".equals(header.trim())){
                	read(key, lengthBuf, in);
                    int length = PIOUtils.redInteger4(lengthBuf, 0);                
                	msgBuf = ByteBuffer.allocate(length-MSG_DETAIL_LENGTH_EXCLUDE);
                	read(key, msgBuf, in);
                	if(out==null){
                		String client = PROXY_HOST[1];
                		out = SocketChannel.open();  
                		out.configureBlocking(false);
                		out.connect(new InetSocketAddress(client, PROXY_PORT));
                		in.register(selector, SelectionKey.OP_READ,out);
                		out.register(selector, SelectionKey.OP_READ,in);
                	}
                	writeAll(headerBuf, lengthBuf, msgBuf, out);
                }else{
                	msgBuf = ByteBuffer.allocate(10*1024);
                	readAll(key, msgBuf, in);
                	if(out==null){
                		String client = PROXY_HOST[0];
                		out = SocketChannel.open();  
                		out.configureBlocking(false);
                		out.connect(new InetSocketAddress(client, PROXY_PORT));
                		in.register(selector, SelectionKey.OP_READ,out);
                		out.register(selector, SelectionKey.OP_READ,in);
                	}
                	writeAll(headerBuf, lengthBuf, msgBuf, out);
                }
            } catch (IOException e) {
                e.printStackTrace();
                cancelKey(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
    
    private static void read(SelectionKey key,ByteBuffer buffer,SocketChannel in)
			throws IOException {
		int count;
		while(buffer.position() < buffer.capacity()) {
		 	count =in.read(buffer);
		 	if (count == -1) {
		 		cancelKey(key);
		        return;
		     }
		 }
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
	
	private static void writeAll(ByteBuffer headerBuf, ByteBuffer lengthBuf,
			ByteBuffer msgBuf, SocketChannel out) throws IOException, InterruptedException {
		ByteBuffer dsc = ByteBuffer.allocate(BUFFER_ALLOC_SIZE+MSG_DETAIL_LENGTH_EXCLUDE);
		putToBuffer(headerBuf, dsc);
		putToBuffer(lengthBuf, dsc);
		putToBuffer(msgBuf, dsc);
		dsc.flip();
		while(!out.finishConnect() ){
            Thread.sleep(25);
        }
		while(dsc.hasRemaining()) {
			out.write(dsc);
		}
		//System.out.println(PIOUtils.redString(dsc, 0,dsc.limit(), UTF8));
	}
	
	private static void putToBuffer(ByteBuffer src, ByteBuffer dsc) throws IOException {
		if(src!=null){
			src.flip();
			if(src.hasRemaining()){
				dsc.put(src);
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
