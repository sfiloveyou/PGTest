package com.sf.pg.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client extends Base{
	
	public Client() throws IOException {
		selector = Selector.open();
	}

	public SocketChannel getDbChannel(String db,int port) throws IOException {
		SocketChannel dbChannel = SocketChannel.open();  
		dbChannel.configureBlocking(false);
		dbChannel.connect(new InetSocketAddress(db,port));
		//dbChannel.register(selector, SelectionKey.OP_CONNECT);
		return dbChannel;
	}

	void handler(SelectionKey key) throws IOException{
		if (key.isConnectable()) {
			SocketChannel dbChannel = (SocketChannel) key.channel();
            dbChannel.finishConnect();
            //key.interestOps(SelectionKey.OP_READ);
            System.out.println("client started");
        	//ByteBuffer buffer = PacketUtils.makeStartUpPacket("postgres", "postgres");
			//write(buffer, dbChannel);
        } else if (key.isValid() && key.isReadable()) {
    		SocketChannel in = (SocketChannel) key.channel();
        	SocketChannel out = (SocketChannel) key.attachment();
        	ByteBuffer buffer = ByteBuffer.allocate(BUFFER_ALLOC_SIZE);
            read(key, buffer, in);
            write(buffer, out);
        }
	}
	
	
}
