package shared.remote;

import shared.interfaces.SubserverInterface;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

public class SubserverData implements Serializable {
	public SubserverInterface server;
	public final ConcurrentHashMap<String, ClientData> users = new ConcurrentHashMap<>();
	
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
