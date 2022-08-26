package shared;

import shared.interfaces.SubserverInterface;

import java.io.Serializable;
import java.util.HashMap;

public class SubserverData implements Serializable {
	public SubserverInterface server;
	public final HashMap<String, ClientData> users = new HashMap<>();
	
	public int id;
	public long wakeupTime;
	
	public transient Thread thread;
	
	public SubserverData(SubserverInterface subserver, int id, long wakeupTime) {
		this.server = subserver;
		this.id = id;
		this.wakeupTime = wakeupTime;
	}
	
	@Override
	public String toString() {
		return "[" + id + "]";
	}
}
