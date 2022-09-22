package shared;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class Utils {
	public static final int PACKAGE_SIZE = 1024 * 1024 * 16; // 16MB
	public static final String TIMEOUT = "7000";
	
	public static void cleanup(String location) {
		Path path = Path.of(location);
		
		if (Files.exists(path)) {
			File[] files = (new File(location)).listFiles();
			
			if (files != null) for (File file : files) file.delete();
		}
	}
	
	public static void sleep(int time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException ignored) {
		}
	}
}
