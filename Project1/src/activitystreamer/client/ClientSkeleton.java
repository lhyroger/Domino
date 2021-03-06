package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;

import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private static final String SECRET = "fmnmpp3ai91qb3gc2bvs14g3ue";
	private TextFrame textFrame;
	private BufferedReader reader;
	private PrintWriter writer;
	private Socket socket;
	

	
	public static ClientSkeleton getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}
	
	public ClientSkeleton(){
		
		
		textFrame = new TextFrame();
		
	}
	
	
	
	
	
	
	@SuppressWarnings("unchecked")
	//sent from textFrame
	public void sendActivityObject(JSONObject activityObj){
		String msg;
		String cmd = getCommand(activityObj.toJSONString());
		if (cmd == "LOGOUT") {
			msg = activityObj.toJSONString();
		}else {
			JSONObject json = activityObj;
			JSONObject js = new JSONObject();
			String username = Settings.getUsername();
			String secret = Settings.getSecret();
			if (json.containsKey("authenticated_user")) {
				json.replace("authenticated_user", username);
			}
			json.put("authenticated_user", username);
			js.put("command", "ACTIVITY_MESSAGE");
			js.put("username", username);
			js.put("secret", secret);
			js.put("activity", json);
			msg = js.toJSONString();
			
		}
		sendMessage(msg);
	}
	
	
	public void disconnect(){
		sendMessage("{\"command\":\"LOGOUT\"}");
		closeSocket();
		System.exit(-1);
	}
	
	private void closeSocket() {
		try {
			socket.close();
		}catch (IOException e) {
			log.error("Error of closing the socket");
		}
	}
	
	private void sendMessage(String cmd) {
		if (getCommand(cmd)=="LOGOUT") {
			writer.println(cmd);
			closeSocket();
			System.exit(-1);
			
		}else {
			
			writer.println(cmd);
					
		}
	}
	
	private void printTextFrame(String msg) {
		
			textFrame.setOutputText(toJson(msg));
		
		
	}
	
	public void run(){
//		String response = null;
//		try {
//			response = reader.readLine();
//		} catch (ConnectException|NullPointerException e) {
//			log.error("Connection Failure: " + e + " Disconnecting...");
//			
//		} catch (IOException e) {
//		
//			log.error("Cannot read reponse");
//		}
//		
//		log.info("should return response" + response);
//		processResponse(response);
//		
//		
		while(true) {
			String response;
			try {
				response = reader.readLine();
			} catch (ConnectException|NullPointerException e) {
				log.error("Connection Failure: " + e + " Disconnecting...");
				break;
			} catch (IOException e) {
				
				break;
			}
			//log.info(response);
			processResponse(response);
		}
		
		
	}

	//
	private void processResponse(String response) {
		boolean hasError = true;
		String cmd = getCommand(response);
		switch (cmd){
			case "INVALID_MESSAGE":
				printTextFrame(response);
				break;
			case "AUTHTENTICATION_FAIL":
				printTextFrame(response);
				break;
			case "LOGIN_SUCCESS":
				hasError = false;
				printTextFrame(response);
				break;
			case "LOGIN_FAILED":
				printTextFrame(response);
				break;
			case "REDIRECT":
				printTextFrame(response);
				redirect(response);
				break;
			case "REGISTER_FAILED":
				printTextFrame(response);
				break;
			case "REGISTER_SUCCESS":
				hasError = false;
				printTextFrame(response);
				break;
			case "LOCK_DENIED":
				printTextFrame(response);
				break;
			case "ACTIVITY_BROADCAST":
				hasError = false;
				printTextFrame(response);
				break;
			default:
				hasError = true;
		}
		if (hasError) {
			closeSocket();
			//System.exit(-1);
		}
	}
	
	private void redirect(String response) {
		JSONObject json = toJson(response);
		Settings.setRemoteHostname((String)json.get("hostname"));
		Settings.setRemotePort((int)json.get("port"));
		closeSocket();
		startConnection();
	}

	private JSONObject toJson(String msg) {
		JSONObject json = null;
		try {
			json = (JSONObject) new JSONParser().parse(msg);
		} catch (ParseException e) {
			log.error("JSON parse error while parsing message");
		}
		return json;
	}

	private String getCommand(String response) {
		JSONObject json = null;
		try {
			json = (JSONObject) new JSONParser().parse(response);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (String) json.get("command");
	}

	public void startConnection(){
		String remoteHostname = Settings.getRemoteHostname();
		int remotePort = Settings.getRemotePort();
		try {
			socket = new Socket(remoteHostname, remotePort); 
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream(),true);
		} catch (IOException e) {
			log.fatal("Connection failure: " + e);
			System.exit(-1);
		}
		startProcess();
	}

	private void startProcess() {
		String username = Settings.getUsername();
		String secret = Settings.getSecret();
		//register
		if (!username.equals("anonymous") && secret == null) {
			register(username);
		}else { //login
			login(username, secret);
		}		
	}

	@SuppressWarnings("unchecked")
	private void login(String username, String secret) {
		JSONObject json = new JSONObject();
		json.put("command", "LOGIN");
		json.put("username", username);
		json.put("secret", secret);
		sendMessage(json.toJSONString());
		
	}

	@SuppressWarnings("unchecked")
	private void register(String username) {
		JSONObject json = new JSONObject();
		json.put("command", "REGISTER");
		json.put("username", username);
		json.put("secret", Settings.nextSecret());
		sendMessage(json.toJSONString());
	}

	
}
