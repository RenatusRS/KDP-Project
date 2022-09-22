package server;

import javax.security.auth.login.LoginException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

public class Logger {
	private final Consumer<String> print;
	
	public Logger(Consumer<String> print) {
		this.print = print;
	}
	
	public void info(String text) {
		print.accept("[INFO] [" + (new SimpleDateFormat("HH:mm:ss").format(new Date())) + "] " + text + ".\n");
	}
	
	public void error(String text, String err) throws LoginException {
		print.accept("[ERROR] [" + (new SimpleDateFormat("HH:mm:ss").format(new Date())) + "] " + text + ".\n");
		throw new LoginException(err);
	}
}
