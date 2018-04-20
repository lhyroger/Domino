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
	public void sendActivityObject(JSONObject activityObj){
		String msg;
		msg = activityObj.toString();
		sendMessage(msg);
	}
	
	
	public void disconnect(){
		sendMessage("LOGOUT");
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
		if (cmd == "LOGOUT") {
			writer.println("You are logged out!");
			
		}else {//placeholder
			writer.println(cmd);
			JSONParser parser = new JSONParser();
			JSONObject temp;
			try {
				temp = (JSONObject)parser.parse(cmd);
				String temp2 = temp.get("command").toString();
				log.error(temp2);
				textFrame.setOutputText(temp);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	public void run(){
		String response = null;
		try {
			response = reader.readLine();
		} catch (ConnectException|NullPointerException e) {
			log.error("Connection Failure: " + e + " Disconnecting...");
			
		} catch (IOException e) {
		
			log.error("Cannot read reponse");
		}
		
		log.info("should return response" + response);
		processResponse(response);
		
		
//		while(true) {
//			String response;
//			try {
//				response = reader.readLine();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				log.error("Cannot read response");
//				break;
//			}
//			log.info(response);
//			processResponse(response);
//		}
		
		
	}

	
	private void processResponse(String res) {
		//if res
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
		}
	}

	
}
