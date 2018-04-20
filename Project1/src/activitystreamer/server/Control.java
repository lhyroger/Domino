package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import activitystreamer.util.Settings;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static ArrayList<Connection> clientConnections;
	private static ArrayList<Connection> serverConnections;
	//created users interface that is not functional, maybe use other method?
	private static ArrayList<Users> registedUser; 
	private static boolean term=false;
	private static Listener listener;
	
	protected static Control control = null;
	
	public static Control getInstance() {
		if(control==null){
			control=new Control();
		} 
		return control;
	}
	
	public Control() {
		// initialize the connections array
		connections = new ArrayList<Connection>();
		// start a listener
		try {
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}	
	}
	
	public void initiateConnection(){
		// make a connection to another server if remote hostname is supplied
		if(Settings.getRemoteHostname()!=null){
			try {
				outgoingConnection(new Socket(Settings.getRemoteHostname(),Settings.getRemotePort()));
			} catch (IOException e) {
				log.error("failed to make connection to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" :"+e);
				System.exit(-1);
			}
		}
	}
	
	/*
	 * Processing incoming messages from the connection.
	 * Return true if the connection should close.
	 */
	public synchronized boolean process(Connection con,String msg){
		boolean hasError;
		String command;
		command = getCommand(msg);
		switch (command){
			case "AUTHENTICATE":
				hasError = processAuthenticate(con, msg);
				break;
			case "LOGIN":
				hasError = processLogin(con, msg);
				break;
			case "REDIRECT":
				hasError = processRedirect(con, msg);
				break;
			case "LOGOUT":
				hasError = true;
				break;
			case "ACTIVITY_MESSAGE":
				hasError = processActivityMessage(con,msg);
				break;
			case "SERVER_ANNOUNCE":
				hasError = processServerAnnounce(con, msg);
				break;
			case "ACTIVITY_BROADCAST":
				hasError = processActivityBroadcast(con, msg);
				break;
			case "REGISTER":
				hasError = processRegister(con, msg);
				break;
			case "LOCK_REQUEST":
				hasError = processLockRequest(con, msg);
				break;
			case "LOCK_DENIED":
				hasError = processLockDenied(con, msg);
				break;
			default:
				hasError = true;
				break;
		}
		return hasError;
	}
	
	private boolean processLockDenied(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processLockRequest(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processRegister(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processActivityBroadcast(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processServerAnnounce(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processActivityMessage(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processRedirect(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processLogin(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processAuthenticate(Connection con, String command) {
		// TODO Auto-generated method stub
		return false;
	}

	private String getCommand(String msg) {
		JSONObject json = null;
		try {
			json = (JSONObject) new JSONParser().parse(msg);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (String) json.get("command");
	}

	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con){
		if(!term) connections.remove(con);
	}
	
	/*
	 * A new incoming connection has been established, and a reference is returned to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException{
		log.debug("incomming connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
		
	}
	
	/*
	 * A new outgoing connection has been established, and a reference is returned to it
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException{
		log.debug("outgoing connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
		
	}
	
	@Override
	public void run(){
		log.info("using activity interval of "+Settings.getActivityInterval()+" milliseconds");
		while(!term){
			// do something with 5 second intervals in between
			try {
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("received an interrupt, system is shutting down");
				break;
			}
			if(!term){
				log.debug("doing activity");
				term=doActivity();
			}
			
		}
		log.info("closing "+connections.size()+" connections");
		// clean up
		for(Connection connection : connections){
			connection.closeCon();
		}
		listener.setTerm(true);
	}
	
	public boolean doActivity(){
		return false;
	}
	
	public final void setTerm(boolean t){
		term=t;
	}
	
	public final ArrayList<Connection> getConnections() {
		return connections;
	}
}
