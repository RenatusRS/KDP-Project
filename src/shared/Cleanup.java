package shared;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class Cleanup {
	public static void cleanup(String location) {
		Path path = Path.of(location);
		
		if (Files.exists(path)) {
			File[] files = (new File(location)).listFiles();
			
			if (files != null) for (File file : files) file.delete();
		}
	}
}
