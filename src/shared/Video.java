package shared;

import java.io.Serializable;

public class Video implements Serializable {
	public final byte[] data;
	
	public final int size;
	
	public final String name;
	
	public Video(String name, byte[] data, int size) {
		this.name = name;
		this.data = data;
		this.size = size;
	}
	
	@Override
	public String toString() {
		return name;
	}
}
