package myagent;

public class DBAgent {
	java.sql.Connection DBconn;
	
	DBAgent(){
    	try{
    		Class.forName("org.sqlite.JDBC");
    		DBconn = java.sql.DriverManager.getConnection("jdbc:sqlite:d:/log.sqlite", "root", "123");
    		
    		String [] init_cmd = new String[] { 
    				"CREATE TABLE IF NOT EXISTS log (id BIGINT PRIMARY KEY, name VARCHAR(255), loc VARCHAR(255), start_time INT, end_time INT)",
    				"CREATE INDEX IF NOT EXISTS log_name ON log(name)"
    				};
    		
    		for (String sql : init_cmd){
    			DBconn.createStatement().execute(sql);
    		}
    		
    	}catch(Exception e){ e.printStackTrace(); System.exit(100); }
	}
	
	
	void traceEnter(){
		//	try{
		//    	java.sql.Statement st = DBconn.createStatement();
		//    	String sql = String.format("UPDATE log SET end_time = %d WHERE id = %d",
		//    			System.currentTimeMillis(),
		//    			tmp.hashCode()
		//    			);
		//    	//System.out.println(sql);
		//		st.execute(sql);
		//	}catch(Exception e){ e.printStackTrace(); }
	}
	
	void traceExit(){
		//	try{
		//		java.sql.Statement st = DBconn.createStatement();
		//    	String sql = String.format("INSERT OR IGNORE INTO log(id,name, loc, start_time) VALUES(%d,'%s','%s',%d)",
		//    			tmp.hashCode(),
		//    			tmp.name(),
		//    			tmp.location(),
		//    			System.currentTimeMillis()
		//    			);
		//    	//System.out.println(sql);
		//		st.execute(sql);
		//	}catch(Exception e){ e.printStackTrace(); }
	}
	
	protected void finalize() {
    	//try{
    	//	DBconn.close();
    	//}catch(Exception e){ e.printStackTrace(); }
	}
}
