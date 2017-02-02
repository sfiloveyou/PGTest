package com.sf.pg;

import io.mycat.backend.postgresql.utils.PIOUtils;
import io.mycat.backend.postgresql.utils.PacketUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PGProxyServer {
	
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
	private final static List<MyChannel> channelList = new ArrayList<MyChannel>();
	private final static Map<SocketChannel,SocketChannel> channelMap = new HashMap<SocketChannel,SocketChannel>();
	private static Selector selector;
	private static Selector readSelector;
	
	public  static void main(String[] args) {
		try {
			selector = Selector.open();
			readSelector = Selector.open();
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(new Runnable(){
			public void run() {
				try{
					ServerSocketChannel serverChannel = ServerSocketChannel.open();
					serverChannel.configureBlocking(false);
					serverChannel.socket().bind(new InetSocketAddress(SERVER_PORT));
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
		
		//for (int i = 1; i < PROXY_HOST.length; i++) {
			String client = PROXY_HOST[1];
			new Thread(new Runnable(){
				public void run() {
					try{
						SocketChannel socketChannel = SocketChannel.open();  
			            socketChannel.configureBlocking(false);
			            socketChannel.connect(new InetSocketAddress(client,PROXY_PORT));
			            socketChannel.register(readSelector, SelectionKey.OP_CONNECT);
			            MyChannel mychannel = new MyChannel();
			            mychannel.setClient(client);
			            mychannel.setSocketChannel(socketChannel);
			            mychannel.setAuthed(false);
			            channelList.add(mychannel);
			            while (true) {
			            	readSelector.select(20);
							Iterator<?> iterator = readSelector.selectedKeys().iterator();
							while (iterator.hasNext()) {
								SelectionKey key = (SelectionKey) iterator.next();
								iterator.remove();
								clientHandler(key,mychannel);
							}
						}
					}catch(IOException e){
						e.printStackTrace();
					}
				}
			}).start();
		//}
		new Thread(new Runnable(){
			public void run() {
				while (true) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					for (MyChannel myChannel : channelList) {
						if(myChannel.isAuthed()){
							try {
								heartbeat(myChannel);
							} catch (UnsupportedEncodingException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
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
    		System.out.println("server channel connected");
    		
    		SocketChannel clientChannel = SocketChannel.open();  
    		clientChannel.configureBlocking(false);
    		clientChannel.connect(new InetSocketAddress(PROXY_HOST[0], PROXY_PORT));
    		channel.register(selector, SelectionKey.OP_READ,clientChannel);
    		clientChannel.register(selector, SelectionKey.OP_READ,channel);
    		System.out.println("client channel connected");
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
                	writeAll(headerBuf, lengthBuf, msgBuf, out);
                	//out = channelList.get(0).getSocketChannel();
                }else{
                	msgBuf = ByteBuffer.allocate(10*1024);
                	readAll(key, msgBuf, in);
                	writeAll(headerBuf, lengthBuf, msgBuf, out);
                }
            } catch (IOException e) {
                e.printStackTrace();
                cancelKey(key);;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
	}
    
    protected static void clientHandler(SelectionKey key, MyChannel myChannel) throws IOException {
		SocketChannel in = (SocketChannel) key.channel();
    	if (key.isConnectable()) {
    		in.finishConnect();
    		key.interestOps(SelectionKey.OP_READ);
            //if(!PROXY_HOST[0].equals(client)){
            	authClient(in);
            	System.out.println("authClient "+myChannel.getClient());
            //}
            myChannel.setAuthed(true);
        }else if (key.isValid() && key.isReadable()) {
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
            }else{
            	msgBuf = ByteBuffer.allocate(10*1024);
            	readAll(key, msgBuf, in);
            }
            printAll(headerBuf, lengthBuf, msgBuf);
        }
	}
	private static void printAll(ByteBuffer headerBuf,ByteBuffer lengthBuf, ByteBuffer msgBuf) throws IOException {
		ByteBuffer dsc = ByteBuffer.allocate(msgBuf.limit()+MSG_DETAIL_LENGTH_EXCLUDE);
		putToBuffer(headerBuf, dsc);
		putToBuffer(lengthBuf, dsc);
		putToBuffer(msgBuf, dsc);
		dsc.flip();
		//System.out.println("msg::"+PIOUtils.redString(dsc, 0,dsc.limit(), UTF8));
	}
    
    private static void authClient(SocketChannel sc) throws IOException {
		ByteBuffer buf = PacketUtils.makeStartUpPacket(PROXY_USER, PROXY_DB);
		buf.flip();
		buf.remaining();
		while(buf.hasRemaining()) {
			sc.write(buf);
		}
	}
    
	private static void heartbeat(MyChannel myChannel)
			throws UnsupportedEncodingException, IOException {
		ByteBuffer b = ByteBuffer.allocate(60);
		b.put("Q".getBytes());
		b.put(new byte[]{0,0,0,14});
		b.put("select 1;".getBytes("UTF-8"));
		b.put(new byte[]{0});
		b.flip();
		while(b.hasRemaining()) {
			myChannel.getSocketChannel().write(b);
		}
		System.out.println("select 1 to "+myChannel.getClient());
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
	
	private static void write(ByteBuffer buffer,SocketChannel out)
			throws IOException {
		buffer.flip();
		while(buffer.hasRemaining()) {
			out.write(buffer);
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
        SocketChannel mappedChannel = (SocketChannel) key.attachment();
        SelectionKey mappedKey = mappedChannel.keyFor(key.selector());
        if (mappedKey != null) {
            mappedKey.cancel();
        }
    }
}

class MyChannel{
	private SocketChannel socketChannel;
	private String client;
	private boolean isAuthed;
	public SocketChannel getSocketChannel() {
		return socketChannel;
	}
	public void setSocketChannel(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
	public boolean isAuthed() {
		return isAuthed;
	}
	public void setAuthed(boolean isAuthed) {
		this.isAuthed = isAuthed;
	}
	public String getClient() {
		return client;
	}
	public void setClient(String client) {
		this.client = client;
	}
	
}