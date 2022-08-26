package shared;

import java.io.Serializable;

public class Data implements Serializable {
	public final byte[] data;
	
	public final int size;
	
	public Data(byte[] data, int size) {
		this.data = data;
		this.size = size;
	}
}
