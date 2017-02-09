package com.sf.pg.server;

import io.mycat.backend.postgresql.utils.PIOUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Server extends Base{
	static Client client;
	
	public Server() throws IOException {
		selector = Selector.open();
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(new InetSocketAddress(SERVER_PORT));
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("server started");
	}

	protected ByteBuffer getBuffer(ByteBuffer src, int length)
			throws IOException {
		ByteBuffer out  = ByteBuffer.allocate(length);
		for(int i=0 ;i< length;i++){
			out.put(new byte[]{src.get()});
		}
		return out;
	}
	
	void handler(SelectionKey key) throws IOException {
    	if (key.isAcceptable()) {
    		ServerSocketChannel server = (ServerSocketChannel) key.channel();
    		SocketChannel channel = server.accept();
    		channel.configureBlocking(false);
    		channel.register(selector, SelectionKey.OP_READ);
        }else if (key.isValid() && key.isReadable()) {
        	SocketChannel in = (SocketChannel) key.channel();
        	SocketChannel out = (SocketChannel) key.attachment();
        	String ip = PROXY_HOST[0];
            try {
            	
            	ByteBuffer b = ByteBuffer.allocate(BUFFER_ALLOC_SIZE);
            	read(key, b, in);
            	b.flip();
            	while(b.hasRemaining()){
	            	ByteBuffer headerBuf = getBuffer(b,HEADER_LENGTH);
	            	headerBuf.flip();
	            	ByteBuffer lengthBuf = null;
	            	ByteBuffer msgBuf = null;
	            	ByteBuffer buffer = null;
	            	if(headerBuf.get(0)==0){
	            		lengthBuf = getBuffer(b,MSG_LENGTH-1);
	            		lengthBuf.flip();
	            		ByteBuffer tmp = ByteBuffer.allocate(MSG_LENGTH);
	            		tmp.put(headerBuf);
	            		tmp.put(lengthBuf);
	            		
	            		int length = PIOUtils.redInteger4(tmp, 0);
	            		if(length>0){
	            			msgBuf = getBuffer(b,length-MSG_DETAIL_LENGTH_EXCLUDE);
	                		msgBuf.flip();
		            		buffer = ByteBuffer.allocate(length);
		            		tmp.flip();
		            		buffer.put(tmp);
		            		buffer.put(msgBuf);
	            		}else{
	            			buffer = ByteBuffer.allocate(headerBuf.limit()+lengthBuf.limit());
		            		buffer.put(headerBuf);
		            		buffer.put(lengthBuf);
	            		}
	            	}else{
	            		lengthBuf = getBuffer(b,MSG_LENGTH);
	            		lengthBuf.flip();
	            		int length = PIOUtils.redInteger4(lengthBuf, 0);
	            		if(length>0){
	            			msgBuf = getBuffer(b,length-MSG_DETAIL_LENGTH_EXCLUDE);
	                		msgBuf.flip();
	            		}
	            		
	            		buffer = ByteBuffer.allocate(length+1);
	            		buffer.put(headerBuf);
	            		buffer.put(lengthBuf);
	            		buffer.put(msgBuf);
	            		if(headerBuf.get(0)==81){
	            			ip = "192.168.56.242";
	            		}
	            	}
	            	if(out == null){
		        		System.out.println("client.getDbChannel");
		        		out = client.getDbChannel(ip,PROXY_PORT);
		        		out.register(client.getSelector(), SelectionKey.OP_CONNECT|SelectionKey.OP_READ,in);
		                in.register(selector, SelectionKey.OP_READ,out);
		                while(out == null || !out.isConnected()){
		        		}
		        	}
	                write(buffer, out);
            	}
            } catch (Exception e) {
                e.printStackTrace();
                cancelKey(key);
            } 
        }
	}
	
	public static void main(String[] args) {
		try {
			new Server().run();
			client = new Client();
			client.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	

	
	
}
