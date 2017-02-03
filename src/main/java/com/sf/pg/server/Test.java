package com.sf.pg.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

class Test implements Runnable{
	public static final String URL241 = "jdbc:postgresql://192.168.56.241:5432/postgres";  
	public static final String URL242 = "jdbc:postgresql://192.168.56.242:20004/postgres";  
	public static final String URL243 = "jdbc:postgresql://192.168.56.243:20005/postgres";
	public static final String URL244 = "jdbc:postgresql://192.168.56.244:20004/postgres";
	public static final String URL245 = "jdbc:postgresql://192.168.56.245:20005/postgres";
	public static final String URL246 = "jdbc:postgresql://192.168.56.246:5432/postgres";
	public static final String PROXY = "jdbc:postgresql://127.0.0.1:54320/postgres";
	public static final String[] URLARRAY= {URL242,URL243,URL244,URL245};
	public static final String USERNAME = "postgres";  
	public static final String USERNAME_XC = "pgxc";  
	public static final String PASSWORD = "123456"; 
	private static final Random RANDOM = new Random();
	private String name;
    public Test(String name) {
		try {
			Class.forName("org.postgresql.Driver").newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.name=name;
    }
	public void run() {
		long start,end = 0;
		
		Random r = new Random();
		int nextInt = r.nextInt(4);
		String url = URLARRAY[nextInt];
		start = System.currentTimeMillis();
		System.out.println(name+url+" max id :"+selectMaxId(url,USERNAME_XC,PASSWORD));
		end = System.currentTimeMillis();		
		System.out.println(name+url+" time : "+(end-start));
	}
	
	public static void insert(String url,String user,String pwd){

		ResultSet rs = null;
		Statement stmt = null;
		Connection conn = null; 
		try {
			conn = DriverManager.getConnection(url, user, pwd);
			stmt = conn.createStatement();  
			rs = stmt.executeQuery("select max(id) from t_user");
			int id = 0;
			while (rs.next()) {
				id=rs.getInt(1);
	        }
			for (int i = id+1; i <= id+1000; i++) {
				String sql = "INSERT INTO t_user (name) VALUES('cloud"+i+"')";
				stmt.executeUpdate(sql);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally{
			close(rs, stmt, conn);
		}
		
	} 
	
	public static int selectMaxId(String url,String user,String pwd){
		String sql = "select max(id) from t_user";  
		ResultSet rs = null;
		Statement stmt = null;
		Connection conn = null; 
		try {
			conn = DriverManager.getConnection(url, user, pwd);
			stmt = conn.createStatement();  
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
	              return rs.getInt(1);
	        }
		} catch (SQLException e) {
			e.printStackTrace();
		} finally{
			close(rs, stmt, conn);
		}
		return 0;
	}

	private static void close(ResultSet rs, Statement stmt, Connection conn) {
		if(rs!=null){
			try {
					rs.close(); 
			} catch (Exception e) {
			}
		}
		if(stmt!=null){
			try {
				stmt.close(); 
			} catch (Exception e) {
			}
		}
		if(conn!=null){
			try {
				conn.close(); 
			} catch (Exception e) {
			}
		}
	}
	
	public static void main(String[] args) throws InterruptedException {
		
		for (int i = 0; i < 10; i++) {
			Thread.sleep(2000);
			for (int j = 0; j < 20; j++) {
				new Thread(new Runnable(){
					public void run() {
						//System.out.println(Thread.currentThread());
						try{
							long start,end = 0;
//							start = System.currentTimeMillis();
//							insert(PROXY,USERNAME,PASSWORD);
//							end = System.currentTimeMillis();
//							System.out.println(Thread.currentThread()+" insert time : "+(end-start));
	
							start = System.currentTimeMillis();
							int count = selectMaxId(PROXY,USERNAME,PASSWORD);
							//Thread.sleep(RANDOM.nextInt(10)*100);
							end = System.currentTimeMillis();
							System.out.println(Thread.currentThread()+" selectMaxId time : "+(end-start)+" count "+count);
						}catch(Exception e){
							e.printStackTrace();
						}
					}
				}).start();
			}
		}
	}
}