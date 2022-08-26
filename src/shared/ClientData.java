package shared;

import shared.interfaces.ClientInterface;

import java.io.Serializable;

public class ClientData implements Serializable {
	public final ClientInterface client;
	public final String username;
	public final String password;
	
	public ClientData(ClientInterface client, String username, String password) {
		this.client = client;
		this.username = username;
		this.password = password;
	}
	
	@Override
	public String toString() {
		return "'" + username + "'";
	}
}
