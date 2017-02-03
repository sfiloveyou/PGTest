package com.sf.pg.server;

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


	
	void handler(SelectionKey key) throws IOException {
    	if (key.isAcceptable()) {
    		ServerSocketChannel server = (ServerSocketChannel) key.channel();
    		SocketChannel channel = server.accept();
    		channel.configureBlocking(false);
    		channel.register(selector, SelectionKey.OP_READ);
        }else if (key.isValid() && key.isReadable()) {
        	SocketChannel in = (SocketChannel) key.channel();
        	SocketChannel out = (SocketChannel) key.attachment();
            try {
            	ByteBuffer buffer = ByteBuffer.allocate(BUFFER_ALLOC_SIZE);
            	read(key, buffer, in);
            	if(out == null){
            		out = client.getDbChannel(PROXY_HOST[0],PROXY_PORT);
            		out.register(client.getSelector(), SelectionKey.OP_CONNECT|SelectionKey.OP_READ,in);
                    in.register(selector, SelectionKey.OP_READ,out);
                    while(out == null || !out.isConnected()){
            		}
            	}
            	
                write(buffer, out);
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
