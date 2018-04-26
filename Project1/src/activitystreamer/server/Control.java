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
	private static ArrayList<Connection> clientConnections;
	private static ArrayList<Connection> serverConnections;
	//created users interface that is not functional, maybe use other method?
	private static HashMap<String, String> registeredUser;
	private static final String ServerSECRET = "fmnmpp3ai91qb3gc2bvs14g3ue";
	private static boolean term=false;
	private static Listener listener;
	private String id;
	private static ArrayList<String> serverAnnounceInfo;
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
		clientConnections = new ArrayList<Connection>();
		serverConnections = new ArrayList<Connection>();
		registeredUser = new HashMap<String, String>();
		serverAnnounceInfo = new ArrayList<String>();

		// initialize the connections array
		id = Settings.nextSecret();
		if(Settings.getRemoteHostname() == null) {
		    log.info("Lack of remote host information.");	
		    }
		else {
		    initiateConnection();
		}

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
			case "AUTHENTICATION_FAIL":
			    hasError = true;
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

	private boolean processActivityBroadcast(Connection con, String message) {
		// TODO Auto-generated method stub
	    log.info("Activity broadcast message received from prot: " + con.getSocket().getPort());
        if(!validActivityMsg(con,message)) {
            return true;
        }
        broadcastToAllClient(message);
        for(Connection connection: serverConnections) {
            if(!connection.equals(con)) {
                connection.writeMsg(message);
            }
        }
		return false;
	}

	private boolean processServerAnnounce(Connection con, String message) {
		// TODO Auto-generated method stub
	    log.info("Server announce recieved");
	    if(!serverConnections.contains(con)) {
	        //if the message is received from an unauthenticated server
	        log.info("Announce failed");
	        sendInvalidMessage(con, "this server isn't authenticated");
	        return true;
	    }
	    JSONObject js = toJson(con, message);
	    if(!(js.containsKey("id") && js.containsKey("load") && js.containsKey("hostname") && js.containsKey("port"))) {
	        //if the information is insufficient
	        log.info("Announce failed");
	        sendInvalidMessage(con,"insufficient information");
	        return true;
	    }
	    
	    for(int i = 0; i<serverAnnounceInfo.size(); i++) {
	        JSONObject js1 = toJson(con, serverAnnounceInfo.get(i));
	        if(js1.get("id").toString() != js.get("id").toString()) {
	            serverAnnounceInfo.add(message);
	        }
	        else {
	            serverAnnounceInfo.set(i, message);
	        }
	    }
	    //inform others to update list
	    broadcastToOtherServer(con,message);
	    
		return false;
	}

	private boolean processActivityMessage(Connection con, String message) {
		// TODO Auto-generated method stub
	  log.info("Activity message received from port: " +con.getSocket().getPort());
	  if(!validActivityMsg(con, message)) {
	      return true;
	  }
	  JSONObject js = toJson(con, message);
	  String usrname = js.get("username").toString();
	  String sec = js.get("secret").toString();
	  JSONObject jso = toJson(con, js.get("activity").toString());
      String usern = jso.get("authenticated_user").toString();
	  if(!clientConnections.contains(con) || !registeredUser.containsKey(usrname) || !registeredUser.get(usrname).equals(sec) || !registeredUser.containsKey(usern)) {
	      //if the user is not anoymous or the username and secret do not match the logged in the user
	      //or if the user has not logged in yet
	      log.info("Invalid acivity message received, authentication failed message sent");
	      sendAuthenticationFail(con, "username and secret do not match");
	      return true;
	  }
	  else {
	      log.debug("Broadcast activity message received from client");
	      JSONObject brodj = new JSONObject();
	      String act = js.get("activity").toString();
	      brodj.put("command", "ACTIVITY_BROADCAST");
	      brodj.put("activity", act);
	      String broadac = brodj.toJSONString();
	      broadcastToOtherServer(con, broadac);
	  }
		return false;
	}
	
	private boolean validActivityMsg(Connection con, String message) {
	    JSONObject js = toJson(con, message);
	    if(!js.containsKey("activity")) {
	        sendInvalidMessage(con, "the received message did not contain activity");
	        return false;
	    }
	    return true;
	}

	private boolean processRedirect(Connection con, String message) {
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
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private void sendLoginFailed(Connection con, String failed) {
		JSONObject js = new JSONObject();
		js.put("command", "LOGIN_FAILED");
		js.put("info", failed);
		con.writeMsg(js.toJSONString());
	}

	@SuppressWarnings("unchecked")
	private void sendInvalidMessage(Connection con, String info) {
		JSONObject js = new JSONObject();
		js.put("command", "INVALID_MESSAGE");
		js.put("info", info);
		con.writeMsg(js.toJSONString());
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
	    JSONObject json = toJson(con, message);
	    if(serverConnections.contains(con)) {//if the connection is authenticated
	        log.info("Authentication failed");
	        sendInvalidMessage(con, "this connection is already authenticated");
	        return true;
	    }
	    
        if ( ! json.containsKey("secret")) {
           //when the secret doesn't exist, authentication fails
            log.info("Authentication failed");
            sendInvalidMessage(con, "lack of secret");
            clientConnections.remove(con);
            return true;
        }
        else {
            //when the secret exists, to judge if the secret corrects, then to judge if the connection exists
            String sec = json.get("secret").toString();
	         if (sec.equals(ServerSECRET)) {//when the secret is correct
	           //when the connection doesn't exist, add to the server connections. 
	             clientConnections.remove(con);
	             serverConnections.add(con);
                 return false;
             }
	        else {//when the secret is incorrect
	        	log.info("The secret is incorrect");
	            clientConnections.remove(con);
	            String str = "the supplied secret is incorrect : " + sec;
	            sendAuthenticationFail(con, str);
	            return true;
	         }
	      }
	}

	
	private void sendAuthenticationFail(Connection con, String str) {
        // TODO Auto-generated method stub
        JSONObject js = new JSONObject();
        js.put("command", "AUTHENTICATION_FAIL");
        js.put("info", str);
        con.writeMsg(js.toJSONString());
	    con.closeCon();
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
		}
		
		return json;
	}


	
	/*

	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con){
		if(!term) {
		    if(serverConnections.contains(con))
		        serverConnections.remove(con);
		    else {
		        clientConnections.remove(con);
		    }
		 }
	}
	
	/*
	 * A new incoming connection has been established, and a reference is returned to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException{
		log.debug("incomming connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		clientConnections.add(c);
		return c;
		
	}
	
	/*
	 * A new outgoing connection has been established, and a reference is returned to it
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException{
		log.debug("outgoing connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		serverConnections.add(c);
		sendAuthenticate(c, Settings.getSecret());
		doActivity();
		return c;
		
	}
	
	   private void sendAuthenticate(Connection con, String sec) {
	        JSONObject js = new JSONObject();
	        js.put("command","AUTHENTICATE");
	        js.put("secret", sec);
	        con.writeMsg(js.toJSONString());
	        log.info("Sent authentication information");
	    }
	
	
	@Override
	public void run(){
		log.info("using activity interval of "+Settings.getActivityInterval()+" milliseconds");
		log.debug(Settings.getLocalHostname());
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
		log.info("closing "+clientConnections.size()+" client connections");
		log.info("closing "+serverConnections.size()+" server connections");
		// clean up
		for(Connection connection : clientConnections){
			connection.closeCon();
		}
		for(Connection connection : serverConnections){
            connection.closeCon();
        }
		listener.setTerm(true);
	}
	
	public boolean doActivity(){
	    JSONObject js = new JSONObject();
	    js.put("command", "SERVER_ANNOUNCE");
	    js.put("id", id);
	    js.put("load", clientConnections.size());
	    js.put("hostname", Settings.getLocalHostname());
	    js.put("port", Settings.getLocalPort());
	    String serverAnnouninfo = js.toJSONString();
	    broadcastToOtherServer(null, serverAnnouninfo);
	    log.info("Sever announcement sent");
		return false;
	}
	
	public void broadcastToOtherServer(Connection con, String str) {
	    for(Connection connection: serverConnections) {
	        if(!connection.equals(con))
	            connection.writeMsg(str);
	    }
	    }
	
	
	public void broadcastToAllClient(String str) {
	    for(Connection con: clientConnections) {
	        con.writeMsg(str);
	    }
	}	
	
	public final void setTerm(boolean t){
		term=t;
	}
	
	public final ArrayList<Connection> getClintConnections() {
		return clientConnections;
	}
	
	public final ArrayList<Connection> getServerConnections() {
        return serverConnections;
    }
}