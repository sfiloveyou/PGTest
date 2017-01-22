package com.sf.pg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class rtcp {

	private static rtcp instance = new rtcp();
	private Socket[] sockets = {null, null}; //�����Ҫ���ݽ���������socket����
	private Thread[] threads = {null, null}; //������������߳� 
	
	public rtcp() {
		
	}

	public static void main(String[] args) throws InterruptedException {
		if (args.length == 0){
			help();
			return;
		}
		
		int i = 0;
		for (i = 0; i < 2 && i < args.length; i++){
			String[] s = args[i].split(":");
			//listen
			if (s.length == 2 && s[0].toLowerCase().equals("l")){
				final int num = i;
				final int port = Integer.parseInt(s[1]);
				instance.threads[i] = new Thread(new Runnable(){
					public void run() {
						try{
							instance.listen(num, port);
						}catch(Exception e){
							e.printStackTrace();
						}
					}
				});				
			}
			
			//connect
			if (s.length == 3 && s[0].toLowerCase().equals("c")){
				final int num = i;
				final String host = s[1];
				final int port = Integer.parseInt(s[2]);
				instance.threads[i] = new Thread(new Runnable(){
					public void run() {
						try{
							instance.connect(num, host, port);
						}catch(Exception e){
							e.printStackTrace();
						}
					}
				});
			}
		}
		
		for(Thread t : instance.threads){
			if (t != null) t.start();
		}
		
		for(Thread t : instance.threads){
			if (t != null) t.join();
		}
		
	}
	
	private static void help(){		
		System.out.println("# Usage: \n"
			+ "\t java -jar rtcp.jar stream1 stream2 \n"
			+ "\t streamΪ��l:port��c:host:port \n"
			+ "\t l:port��ʾ����ָ���ı��ض˿� \n"
			+ "\t c:host:port��ʾ����Զ��ָ���Ķ˿� \n"
		);
	}
	
	private Socket getAnother(int num){
		int another = 0;
		if (num == 0) another = 1;
//		while(true){
//			//�Ե�ǰ���м�飬�����ڵȴ���һ������ʱ����ǰ�����ѹر��������
//			if (sockets[num] == null || sockets[num].isClosed()){
//				return null;
//			}
//			if (sockets[another] == null){
//				try {
//					Thread.sleep(1);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//				continue;
//			}else if (sockets[another].isClosed()){
//				sockets[another] = null;
//			}else{
//				break;
//			}
//		}
		
		return sockets[another];
		
	}
	
	@SuppressWarnings("resource")
	private void listen(int num, int port){
		ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(port);
			System.out.println(String.format("[%d] listen on %d", num, port));
		} catch (IOException e) {
			System.out.println(String.format("[%d] can not listen on %d", num, port));
			return;
		} 
    	while(true){
    		try {
				Socket socket = serverSocket.accept();
				synchronized(sockets){
					sockets[num] = socket;
				}
				System.out.println(String.format("[%d] connect from: %s:%d", num, socket.getInetAddress().getHostAddress(), socket.getPort()));
				exchange(num, socket);
				if (!socket.isClosed()) {
					try {
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
	}
	
	private void connect(int num, String host, int port){
		while(true){
			Socket socket = null;
			try {
				socket = new Socket(host, port);
			} catch (UnknownHostException e) {
				System.out.println(String.format("[%d] unknow host: %s", num, host));
				return;
			} catch (IOException e) {
				e.printStackTrace();
				try {
					System.out.println(String.format("[%d] can not connect to %s:%d, retry after 10s", num, host, port));
					Thread.sleep(10000); //retry after 30s
					continue;
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			
			if (socket == null) continue;
			synchronized(sockets){
				sockets[num] = socket;
			}
			System.out.println(String.format("[%d] connect to %s:%d", num, host, port));
			exchange(num, socket);
			if (!socket.isClosed()) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	public static byte[] subBytes(byte[] src, int begin, int count) {
        byte[] bs = new byte[count];
        for (int i=begin; i<begin+count; i++) bs[i-begin] = src[i];
        return bs;
    }
	
	private void exchange(int num, Socket s1){
//		System.out.println(String.format("[%d] ready for exchange", num));
		Socket s2 = null;
		try {
			InputStream ins1 = s1.getInputStream();
	        byte[] buf = new byte[10 * 1024];
	        while(true){
	        	int rlen = ins1.read(buf);
	        	String type = new String(subBytes(buf, 0, 1));
				System.out.println(type);
	        	if("Q".equals(type)){
	        		String len = new BigInteger(1, subBytes(buf, 1, 4)).toString(10);
					System.out.println(len);
	        	}
	        	if (rlen <= 0){
	        		break;
	        	}
	        	
	        	s2 = getAnother(num);
	        	if (s2 != null && s2.isConnected()){
		        	OutputStream ops2 = s2.getOutputStream();
		        	ops2.write(buf, 0, rlen);
		        	System.out.println(String.format("[%d] exchange %d bytes", num, rlen));
	        	}else{
	        		System.out.println(String.format("[%d] discard %d bytes", num, rlen));
	        	}
	        }
		}catch (SocketException e){
			//e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}
		
		synchronized(sockets){
			try {
				if (s1 != null && !s1.isClosed()) {
					s1.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			try {
				if (s2 != null && !s2.isClosed()) {
					s2.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			sockets[num] = null;
		}
		
//		waitAnother(num);

//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		System.out.println(String.format("[%d] connect closed", num));
	}
	
	private void waitAnother(int num){
		int another = 0;
		if (num == 0) another = 1;
		while(sockets[another] != null){
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
