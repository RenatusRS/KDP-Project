package shared;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Video {
	public static String destination;
	
	public final String name;
	public final String owner;
	public boolean finished = false;
	private long lastModified;
	
	public Video(String name, String owner) {
		this.name = name;
		this.owner = owner;
		lastModified = System.currentTimeMillis();
	}
	
	public boolean expired() {
		return !finished && System.currentTimeMillis() > lastModified + 1000 * 60;
	}
	
	public void write(Data data) {
		try (OutputStream os = new FileOutputStream(destination + name, true)) {
			os.write(data.data, 0, data.size);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		lastModified = System.currentTimeMillis();
	}
	
	public static void setDestination(String destination) {
		Video.destination = destination;
		
		Utils.cleanup(destination);
		
		try {
			Files.createDirectories(Path.of(destination));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public long getLastModified() {
		return lastModified;
	}
	
	public FileInputStream read() throws FileNotFoundException {
		return new FileInputStream(destination + name);
	}
	
	public long size() {
		return new File(destination + name).length();
	}
	
	public double percent(long uploaded) {
		return (double) uploaded / size();
	}
}
