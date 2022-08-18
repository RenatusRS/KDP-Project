package shared;

import java.io.Serializable;
import java.util.ArrayList;

public class Room implements Serializable {
	public final String video;
	public final String owner;
	public final ArrayList<String> viewers;
	
	private int ID;
	private long time = 0;
	private boolean paused = false;
	
	private long lastUpdate;
	
	public Room(String video, String owner, ArrayList<String> viewers) {
		this.video = video;
		this.owner = owner;
		this.viewers = viewers;
	}
	
	public void sync(long time, boolean paused) {
		this.time = time;
		this.paused = paused;
		
		this.lastUpdate = System.currentTimeMillis();
	}
	
	public boolean getPaused() {
		return paused;
	}
	
	public long getTime() {
		return time;
	}
	
	public void setID(int ID) {
		this.ID = ID;
	}
	
	public int getID() {
		return ID;
	}
	
	public long getLastUpdate() {
		return lastUpdate;
	}
	
	@Override
	public String toString() {
		return ID + " | " + owner + " | " + video;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		
		if (o == null || getClass() != o.getClass()) return false;
		
		return ID == (((Room) o).ID);
	}
}
