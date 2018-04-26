import org.json.simple.JSONObject;

import activitystreamer.server.Connection;

@SuppressWarnings("unchecked")
	private void sendRegisterSuccess(Connection con, String info) {
		JSONObject js = new JSONObject();
		js.put("command", "REGISTER_SUCCESS");
		js.put("info", info);
		con.writeMsg(js.toJSONString());
		log.info("REGISTER_SUCCESS");
		
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