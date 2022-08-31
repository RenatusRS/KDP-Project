package client;

import shared.Data;
import shared.Room;
import shared.Utils;
import shared.Video;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.ClientInterface;
import shared.interfaces.SubserverInterface;
import shared.remote.ClientData;

import javax.security.auth.login.LoginException;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

public class Client extends UnicastRemoteObject implements ClientInterface {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
		System.setProperty("sun.rmi.transport.tcp.responseTimeout", "7000");
	}
	
	private final transient String centralHost;
	private final transient int centralPort;
	
	String username;
	
	transient SubserverInterface subserver;
	
	private final transient ClientGUI gui = new ClientGUI(this);
	
	long wakeupTime = 0;
	
	private final HashMap<String, Video> videos = new HashMap<>();
	
	private Client(String centralHost, int centralPort) throws RemoteException {
		this.centralHost = centralHost;
		this.centralPort = centralPort;
	}
	
	public void loggedIn(String username, String password, boolean registration) throws RemoteException, LoginException, NotBoundException {
		CentralServerInterface central = (CentralServerInterface) LocateRegistry.getRegistry(centralHost, centralPort).lookup("/Central");
		
		this.subserver = null;
		
		wakeupTime = registration ? central.register(new ClientData(this, username, password)) : central.login(new ClientData(this, username, password));
		
		Video.setDestination("uploads/client/" + username + "/");
		
		gui.setTitle("KDP Project [" + username + ", " + wakeupTime + "]");
		
		gui.mainView();
		
		(new Thread(() -> {
			int counter = 0;
			
			while (true) {
				try {
					if (counter == 5 || wakeupTime != subserver.status()) {
						reset();
						break;
					}
					
					counter = 0;
					
					gui.labelConnection.setText("STATUS: Connected");
					gui.labelConnection.setForeground(new Color(0, 102, 0));
					
					syncVideos();
					syncUsers();
					syncRooms();
					
					Utils.sleep(4000);
					
				} catch (RemoteException | NullPointerException e) {
					counter++;
					gui.labelConnection.setText("STATUS: Disconnected | " + counter + "/5 Tries");
					gui.labelConnection.setForeground(new Color(153, 0, 0));
					
					Utils.sleep(10000);
				} catch (LoginException e) {
					gui.addNotification(e.getMessage());
				}
			}
		})).start();
	}
	
	private void reset() {
		wakeupTime = 0;
		
		if (gui.syncThread != null) gui.syncThread.interrupt();
		if (gui.uploadThread != null) gui.uploadThread.interrupt();
		gui.player.pause(true);
		gui.playVideo("");
		
		Utils.cleanup("uploads/client/" + username + "/");
		
		videos.clear();
		username = null;
		
		gui.loginView();
	}
	
	private void syncVideos() throws RemoteException {
		ArrayList<String> videoNames = subserver.getAllVideoNames();
		for (Video video : videos.values()) if (video.finished) videoNames.remove(video.name);
		
		for (String videoName : videoNames) {
			try {
				subserver.requestVideoFromSubserver(videoName, new ClientData(this, username, null));
				Files.deleteIfExists(Path.of("uploads/client/" + username + "/" + videoName));
				videos.put(videoName, new Video(videoName, null));
			} catch (LoginException e) {
				System.out.println(e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void syncUsers() throws RemoteException, LoginException {
		ArrayList<String> users = subserver.getUsers();
		
		for (Component user : gui.usersPanel.getComponents()) if (user instanceof JCheckBox) users.remove(((JCheckBox) user).getText());
		for (String user : users) if (!user.equals(username)) gui.addUser(user);
	}
	
	private void syncRooms() throws RemoteException, LoginException {
		for (Room room : subserver.getRooms(username)) if (((DefaultComboBoxModel<?>) gui.rooms.getModel()).getIndexOf(room) == -1) gui.addRoom(room);
	}
	
	@Override
	public void uploadSubserverToClient(String video, Data data) {
		videos.get(video).write(data);
	}
	
	@Override
	public void finalizeVideo(String video) {
		videos.get(video).finished = true;
		gui.addNotification("Downloaded video '" + video + "'");
		gui.addVideo(video);
	}
	
	@Override
	public synchronized void assignSubserver(SubserverInterface subserver, String username, long wakeupTime) throws LoginException {
		if (this.wakeupTime != wakeupTime) throw new LoginException("The client " + this.username + " is from a different central server");
		if (!username.equals(this.username)) throw new LoginException("This client is from user " + this.username + " now");
		
		this.subserver = subserver;
	}
	
	public static void main(String[] args) throws RemoteException, UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
		new Client(args.length > 0 ? args[0] : "localhost", args.length > 1 ? Integer.parseInt(args[1]) : 8000);
	}
}
