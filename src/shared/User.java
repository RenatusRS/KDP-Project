package shared;

import shared.interfaces.ClientInterface;

import java.io.Serializable;

public class User implements Serializable {
	public final ClientInterface client;
	public final String username;
	public final String password;
	
	public User(ClientInterface client, String username, String password) {
		this.client = client;
		this.username = username;
		this.password = password;
	}
	
	@Override
	public String toString() {
		return "'" + username + "'";
	}
}
