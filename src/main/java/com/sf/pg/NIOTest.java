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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NIOTest {
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
	private final static Map<String,SocketChannel> channelMap = new HashMap<String,SocketChannel>();
	static boolean authed = false;
	public void initServer(int port) throws IOException {
		selector = Selector.open();
		readSelector = Selector.open();
		new Thread(new Runnable(){
			public void run() {
				try{
					ServerSocketChannel serverChannel = ServerSocketChannel.open();
					serverChannel.configureBlocking(false);
					serverChannel.socket().bind(new InetSocketAddress(port));
					selector = Selector.open();
					serverChannel.register(selector, SelectionKey.OP_ACCEPT);
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}).start();
		
		for (int i = 1; i < PROXY_HOST.length; i++) {
			String client = PROXY_HOST[i];
			new Thread(new Runnable(){
				public void run() {
					try{
						SocketChannel socketChannel = SocketChannel.open();  
			            socketChannel.configureBlocking(false);
			            socketChannel.connect(new InetSocketAddress(client,PROXY_PORT));
			            socketChannel.register(readSelector, SelectionKey.OP_CONNECT);
			            channelMap.put(client,socketChannel);
			            clientHandler(client);
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}).start();
		}
	}
	
	public void listen() throws Exception {
		// 轮询访问selector
		while (true) {
			selector.select(20);
			Iterator<?> ite = selector.selectedKeys().iterator();
			while (ite.hasNext()) {
				SelectionKey key = (SelectionKey) ite.next();
				ite.remove();

				handler(key);
			}
		}
	}
	
	public static void clientHandler(String client) throws IOException
    {
        while(true) {
        	try {
                if (readSelector.select(20) == 0) {
                    continue;
                }
                Iterator<SelectionKey> iterator = readSelector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                	
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
            		SocketChannel src = (SocketChannel) selectionKey.channel();
            		//ServerSocketChannel out = (ServerSocketChannel) selectionKey.attachment();
                    try {
                    	if (selectionKey.isConnectable()) {
                    		src.finishConnect();
                    		selectionKey.interestOps(SelectionKey.OP_READ);
                            if(!PROXY_HOST[0].equals(client)){
                            	registClient(src);
                            }
                        }else if (selectionKey.isValid() && selectionKey.isReadable()) {
                        	if(authed){
	                        	ByteBuffer buffer = ByteBuffer.allocate(10*1024);
	                        	SocketChannel out = channelMap.get(SERVER);
	                        	
	                        	int count = 0;
	                        	while((count = src.read(buffer)) > 0) {
	                        		buffer.flip();
	                                while(buffer.hasRemaining()) {
	                                	out.write(buffer);
	                                }
	                            }
	                            if (count == -1) {
	                                // EOF
	                                cancelKey(selectionKey);
	                            }
                        	}else{
                        		int count = 0;
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
                                String header = PIOUtils.redString(headerBuf,0,UTF8);
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
                                    int length = PIOUtils.redInteger4(lengthBuf, 0);                
                                	msgBuf = ByteBuffer.allocate(length-MSG_DETAIL_LENGTH_EXCLUDE);
                                    while(msgBuf.position() < length-MSG_DETAIL_LENGTH_EXCLUDE) {
                                    	count = sc.read(msgBuf);
                                    	if (count == -1) {
                                            // EOF
                                            cancelKey(selectionKey);
                                            return;
                                        }
                                    }  
                                    int code = PIOUtils.redInteger2(msgBuf, 0);
    								if(code==0){
    									authed = true;
    									System.out.println("authed");
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
					} catch (Exception e) {
						selectionKey.cancel();
						src.close();
					}
                    
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
	
	private static void registClient(SocketChannel sc) throws IOException {
		ByteBuffer buf = PacketUtils.makeStartUpPacket(PROXY_USER, PROXY_DB);
		buf.flip();
		buf.remaining();
		while(buf.hasRemaining()) {
			sc.write(buf);
		}
	}

	/**
	 * 处理请求
	 * 
	 * @param key
	 * @throws IOException
	 */
	public void handler(SelectionKey key) throws Exception {
		if (key.isAcceptable()) {
			handlerAccept(key);
		}else if (key.isValid() && key.isReadable()) {
			handelerRead(key);
		}
	}
	
	public void handlerAccept(SelectionKey key) throws IOException {
		ServerSocketChannel server = (ServerSocketChannel) key.channel();
		SocketChannel channel = server.accept();
		channel.configureBlocking(false);
		SocketChannel socketChannel = SocketChannel.open();  
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(PROXY_HOST[0], PROXY_PORT));
		channel.register(selector, SelectionKey.OP_READ,socketChannel);
		socketChannel.register(selector, SelectionKey.OP_READ,channel);
		channelMap.put(SERVER,socketChannel);
		
	}
	private static void cancelKey(SelectionKey key) {
        key.cancel();
        SocketChannel mappedChannel = (SocketChannel) key.attachment();
        SelectionKey mappedKey = mappedChannel.keyFor(key.selector());
        if (mappedKey != null) {
            mappedKey.cancel();
        }
    }
	
	private static void readAndWrite(SelectionKey key,ByteBuffer buffer,SocketChannel in, SocketChannel out)
			throws IOException {
		read(key, buffer, in);
		write(buffer, out);
	}
	
	private void putToBuffer(ByteBuffer src, ByteBuffer dsc) throws IOException {
		if(src!=null){
			src.flip();
			if(src.hasRemaining()){
				dsc.put(src);
			}
		}
	}
	
	private void writeAll(ByteBuffer headerBuf, ByteBuffer lengthBuf,
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
	
	public void handelerRead(SelectionKey key) throws IOException, InterruptedException {
		SocketChannel in = (SocketChannel) key.channel();
        SocketChannel out = (SocketChannel) key.attachment();
        try {
            ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_LENGTH);
            //readAndWrite(key, headerBuf, in, out);
            read(key, headerBuf, in);
            String header = PIOUtils.redString(headerBuf,0,UTF8);
            ByteBuffer msgBuf = null;
            ByteBuffer lengthBuf = ByteBuffer.allocate(MSG_LENGTH);
            if(header!=null && "Q".equals(header.trim())){
            	read(key, lengthBuf, in);
                int length = PIOUtils.redInteger4(lengthBuf, 0);                
            	msgBuf = ByteBuffer.allocate(length-MSG_DETAIL_LENGTH_EXCLUDE);
            	read(key, msgBuf, in);
            	if(authed){
            		out = channelMap.get(PROXY_HOST[1]);
            	}
            	
            }else{
            	msgBuf = ByteBuffer.allocate(10*1024);
            	readAll(key, msgBuf, in);
            }
            writeAll(headerBuf, lengthBuf, msgBuf, out);
        } catch (IOException e) {
            e.printStackTrace();
            key.cancel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
		
	}
	
	public static void main(String[] args) {
		NIOTest server = new NIOTest();
		try {
			server.initServer(SERVER_PORT);
			server.listen();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
