package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

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
	//private static ArrayList<String> registeredUser;
	private static HashMap<String,String> registeredUser;	
	private static ArrayList<String> serverAnnounceInfo;
	//private static final String SECRET = "fmnmpp3ai91qb3gc2bvs14g3ue";
	private static boolean term=false;
	private static Listener listener;
	private int count;
	private String pendingRegisterUser;
	private Connection pendingClientConnection;
	private HashMap<String, Connection> queue;
	
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
		clientConnections = new ArrayList<Connection>();
		serverConnections = new ArrayList<Connection>();
		registeredUser = new HashMap<String,String>();
		serverAnnounceInfo = new ArrayList<String>();
		pendingRegisterUser = null;
		count = 0;
		pendingClientConnection = null;
		queue = new HashMap<String, Connection>();
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
		command = getCommand(con, msg);
		switch (command){
			case "AUTHENTICATE":
				hasError = processAuthenticate(con, msg);
				break;
			case "LOGIN":
				hasError = processLogin(con, msg);
				break;
			case "REDIRECT":
				hasError = processAuthtenticationFail(con, msg);
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
			case "LOCK_ALLOWED":
				hasError = processLockAllowed(con, msg);
				break;
			default:
				hasError = true;
				break;
		}
		return hasError;
	}
	
private boolean processLockAllowed(Connection con, String message) {
	if (!serverConnections.contains(con)) {
		sendInvalidMessage(con, "Connection is not authenticated by server");
		return true;
	}
	JSONObject json = toJson(con, message);
	if (json.containsKey("username") && json.containsKey("secret")) {
		String username = (String) json.get("username");
		String secret = (String) json.get("secret");
		
		
		if (pendingRegisterUser.equals(username) && count >0) {
			count--;
		}else if (count == 0) {
			sendRegisterSuccess(con, "register success for " + username + " with secret: " + secret);
			startQueue();
		}
		sendLockAllowed(con, username, secret);
	}else {
		sendInvalidMessage(con, "the recived message did not contain all nesessary key value.");
		return true;
	}
	return false;
}

	private void startQueue() {
		if (!queue.isEmpty()) {
			count = serverAnnounceInfo.size();
			String temp = (String) queue.keySet().toArray()[0];
			pendingClientConnection = queue.get(temp);
			JSONObject jstemp = toJson(pendingClientConnection, temp);
			pendingRegisterUser = (String) jstemp.get("username");
			String sct = (String) jstemp.get("secret");
			sendLockRequest(pendingClientConnection, pendingRegisterUser, sct);
		}
	
}

	private boolean processAuthtenticationFail(Connection con, String message) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processLockDenied(Connection con, String message) {
		if (!serverConnections.contains(con)) {
			sendInvalidMessage(con, "Connection is not authenticated by server");
			return true;
		}
		JSONObject json = toJson(con, message);
		if (json.containsKey("username") && json.containsKey("secret")) {
			String username = (String) json.get("username");
			String secret = (String) json.get("secret");
			if (registeredUser.containsKey(username)) {
				if (registeredUser.get(username).equals(secret)) {
					registeredUser.remove(username);
				}
			}
//			if (registeredUser.contains(username) && secret.equals(SECRET)) {
//				registeredUser.remove(username);
//			}
			
			if (pendingRegisterUser.equals(username) && count >0) {
				count = 0;
				sendRegisterFailed(pendingClientConnection,"the username already exist");
				pendingClientConnection.closeCon();
				connectionClosed(pendingClientConnection);
				pendingClientConnection = null;
				pendingRegisterUser = null;
				startQueue();
				
			}
			sendLockDenied(con, username, secret);
		}else {
			sendInvalidMessage(con, "the recived message did not contain all nesessary key value.");
			return true;
		}
		
		
		return false;
	}

	private boolean processLockRequest(Connection con, String message) {
		if (!serverConnections.contains(con)) {
			sendInvalidMessage(con, "Connection is not authenticated by server");
			return true;
		}
		JSONObject json = toJson(con, message);
		if (json.containsKey("username") && json.containsKey("secret")) {
			String username = (String) json.get("username");
			String secret = (String) json.get("secret");
			if (registeredUser.containsKey(username)) {
				sendLockDenied(con, username, secret);
				return true;
			}else {
				sendLockAllowed(con, username, secret);
			}
		}else {
			sendInvalidMessage(con, "the recived message did not contain all nesessary key value.");
			return true;
		}
		
		return false;
	}

	@SuppressWarnings("unchecked")
	private void sendLockAllowed(Connection con, String username, String secret) {
		for (Connection connection: serverConnections) {
			if (connection != con) {
				JSONObject js = new JSONObject();
				js.put("command", "LOCK_ALLOWED");
				js.put("username", username);
				js.put("secret", secret);
				con.writeMsg(js.toJSONString());
				log.info("LOCK_ALLOWED sent");
			}
		}
		
	}

	@SuppressWarnings("unchecked")
	private void sendLockDenied(Connection con, String username, String secret) {
		for (Connection connection: serverConnections) {
			if (connection != con) {
				JSONObject js = new JSONObject();
				js.put("command", "LOCK_DENIED");
				js.put("username", username);
				js.put("secret", secret);
				con.writeMsg(js.toJSONString());
				log.info("LOCK_DENIED sent");
			}
		}
		
	}

	private boolean processRegister(Connection con, String message) {
		JSONObject json = toJson(con, message);
		if (json.containsKey("username") && json.containsKey("secret")) {
			String username = (String) json.get("username");
			String secret = (String) json.get("secret");
			if (clientConnections.contains(con)) {
				sendInvalidMessage(con, "the user has already registered");
			}
			if (registeredUser.containsKey(username)) {
				sendRegisterFailed(con, username + " is already registered with the system");
				return true;
			}else {
				if (serverConnections.size()==0) {
					registeredUser.put(username, secret);
					sendRegisterSuccess(con, "register success for " + username + " with secret: " + secret);
					return false;
				}else {
					registeredUser.put(username, secret);
					if (count > 0) {
						queue.put(message, con);
					}
					count = serverConnections.size();
					pendingRegisterUser = username;
					pendingClientConnection = con;
					sendLockRequest(con, username, secret);
					
				}
			}
		}else {
			sendInvalidMessage(con, "the recived message did not contain all nesessary key value.");
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private void sendLockRequest(Connection con, String username, String secret) {
		JSONObject js = new JSONObject();
		js.put("command", "LOCK_REQUEST");
		js.put("username", username);
		js.put("secret", secret);
		con.writeMsg(js.toJSONString());
		log.info("LOCK_REQUEST sent");
		
	}

	@SuppressWarnings("unchecked")
	private void sendRegisterSuccess(Connection con, String info) {
		JSONObject js = new JSONObject();
		js.put("command", "REGISTER_SUCCESS");
		js.put("info", info);
		con.writeMsg(js.toJSONString());
		log.info("REGISTER_SUCCESS");
		
	}

	@SuppressWarnings("unchecked")
	private void sendRegisterFailed(Connection con, String info) {
		JSONObject js = new JSONObject();
		js.put("command", "REGISTER_FAILED");
		js.put("info", info);
		con.writeMsg(js.toJSONString());
		log.info("REGISTER_FAILED sent");
		
	}

	private boolean processActivityBroadcast(Connection con, String message) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processServerAnnounce(Connection con, String message) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processActivityMessage(Connection con, String message) {
		// TODO Auto-generated method stub
		return false;
	}

	

	private boolean processLogin(Connection con, String message) {
		JSONObject json = toJson(con, message);
		if (json.containsKey("username") && json.containsKey("secret")) {
			String username = (String) json.get("username");
			String secret = (String) json.get("secret");
			//login as anonymous
			if (username.equals("anonymous")) {
				if (secret == null) {
					sendLoginSuccess(con, username);
					return redirect(con);
				}else {
					sendInvalidMessage(con, "Trying login as a anonymous with a secret.");
					return true;
				}
			//login as user
			}else {
				//has the username
				if (registeredUser.containsKey(username)) {
					if (registeredUser.get(username).equals(secret)) {
						if (!clientConnections.contains(con)) {
							sendLoginSuccess(con, username);						
							return redirect(con);
						}else {
							sendLoginFailed(con, "user " + username + " already connected to the server. Please logout first.");
							return true;
						}
					}else {
						sendLoginFailed(con, "attempt to login with wrong secret");
						return true;
					}
				//!has the username
				}else {
					sendLoginFailed(con, "username does not exist");
					return true;
				}
			}
		}else {
			sendInvalidMessage(con, "the recived message did not contain all nesessary key value.");
			return true;
		}
	}

	@SuppressWarnings("unchecked")
	private boolean redirect(Connection con) {
		for (String info: serverAnnounceInfo) {
			JSONObject json = toJson(con, info);
			int load = (int) json.get("load");
			if(clientConnections.size() - load >= 2) {
				JSONObject js = new JSONObject();
				js.put("command", "REDIRECT");
				js.put("hostname", json.get("hostname"));
				js.put("port", json.get("port"));
				con.writeMsg(js.toJSONString());
				log.info("REDIRECT sent, closing connection...");
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private void sendLoginFailed(Connection con, String failed) {
		JSONObject js = new JSONObject();
		js.put("command", "LOGIN_FAILED");
		js.put("info", failed);
		con.writeMsg(js.toJSONString());
		log.info("LOGIN_FAILED sent");
	}

	@SuppressWarnings("unchecked")
	private void sendInvalidMessage(Connection con, String info) {
		JSONObject js = new JSONObject();
		js.put("command", "INVALID_MESSAGE");
		js.put("info", info);
		con.writeMsg(js.toJSONString());
		log.info("INVALID_MESSAGE sent");
	}

	@SuppressWarnings("unchecked")
	private void sendLoginSuccess(Connection con, String username) {
		JSONObject js = new JSONObject();
		js.put("command", "LOGIN_SUCCESS");
		js.put("info", "logged in as user " + username);
		con.writeMsg(js.toJSONString());
		log.info("LOGIN_SUCCESS sent");
	}

	private boolean processAuthenticate(Connection con, String message) {
		// TODO Auto-generated method stub
		return false;
	}

	private String getCommand(Connection con, String msg) {
		JSONObject json = toJson(con, msg);
		return (String) json.get("command");
	}

	private JSONObject toJson(Connection con, String msg) {
		JSONObject json = null;
		try {
			json = (JSONObject) new JSONParser().parse(msg);
		} catch (ParseException e) {
			log.error("Cannot parser the message");
			sendInvalidMessage(con, "JSON parse error while parsing message");
			con.closeCon();
		}
		return json;
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
