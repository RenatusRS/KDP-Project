package client;

import shared.*;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.ClientInterface;
import shared.interfaces.SubserverInterface;

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
	
	
	// TODO
	//  sinhronizuj centralni server,
	//  obradi remote exceptione
	public void loggedIn(String username, String password, boolean registration) throws RemoteException, LoginException, NotBoundException {
		CentralServerInterface central = (CentralServerInterface) LocateRegistry.getRegistry(centralHost, centralPort).lookup("/Central");
		
		this.subserver = null;
		
		wakeupTime = registration ? central.register(new ClientData(this, username, password)) : central.login(new ClientData(this, username, password));
		
		Video.setDestination("uploads/client/" + username + "/");
		
		gui.setTitle("KDP Project [" + username + ", " + wakeupTime + "]");
		
		gui.start();
		
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
					gui.labelConnection.setForeground(Color.GREEN);
					
					syncVideos();
					syncUsers();
					syncRooms();
					
					Utils.sleep(4000);
					
				} catch (RemoteException | NullPointerException e) {
					counter++;
					gui.labelConnection.setText("STATUS: Disconnected | " + counter + "/5 Tries");
					gui.labelConnection.setForeground(Color.RED);
					
					Utils.sleep(10000);
				}
			}
		})).start();
	}
	
	public void reset() {
		wakeupTime = 0;
		
		if (gui.syncThread != null) gui.syncThread.interrupt();
		if (gui.uploadThread != null) gui.uploadThread.interrupt();
		gui.player.pause(true);
		gui.play("");
		
		Utils.cleanup("uploads/client/" + username + "/");
		
		videos.clear();
		username = null;
		
		gui.restart();
	}
	
	public void syncVideos() throws RemoteException {
		ArrayList<String> videoNames = subserver.getAllVideoNames();
		for (Video video : videos.values()) if (video.finished) videoNames.remove(video.name);
		
		for (String videoName : videoNames) {
			try {
				subserver.requestVideoFromSubserver(videoName, username);
				Files.deleteIfExists(Path.of("uploads/client/" + username + "/" + videoName));
				videos.put(videoName, new Video(videoName, null));
			} catch (LoginException e) {
				System.out.println(e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void syncUsers() throws RemoteException {
		ArrayList<String> users = subserver.getUsers();
		
		for (Component user : gui.usersPanel.getComponents()) if (user instanceof JCheckBox) users.remove(((JCheckBox) user).getText());
		for (String user : users) if (!user.equals(username)) addUser(user);
	}
	
	public void syncRooms() throws RemoteException {
		for (Room room : subserver.getRooms(username)) if (((DefaultComboBoxModel<?>) gui.rooms.getModel()).getIndexOf(room) == -1) addRoom(room);
	}
	
	@Override
	public void uploadSubserverToClient(String video, Data data) throws RemoteException {
		videos.get(video).write(data);
	}
	
	@Override
	public void finalizeVideo(String video) throws RemoteException {
		videos.get(video).finished = true;
		gui.addNotification("Downloaded video '" + video + "'");
		gui.addVideo(video);
	}
	
	@Override
	public void addRoom(Room room) throws RemoteException {
		if (gui.rooms.getItemCount() == 0) {
			Room localRoom = new Room("Local Room", username, null);
			localRoom.setID(0);
			gui.rooms.addItem(localRoom);
		}
		
		gui.addRoom(room);
	}
	
	@Override
	public synchronized void addUser(String user) throws RemoteException {
		gui.addUser(user);
	}
	
	@Override
	public synchronized void assignSubserver(SubserverInterface subserver, String username, long wakeupTime) throws RemoteException, LoginException {
		if (this.wakeupTime != wakeupTime) throw new LoginException("The client " + this.username + " is from a different central server");
		if (!username.equals(this.username)) throw new LoginException("This client is from user " + this.username + "now");
		
		this.subserver = subserver;
	}
	
	public static void main(String[] args) throws RemoteException, UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
		new Client(args.length > 0 ? args[0] : "localhost", args.length > 1 ? Integer.parseInt(args[1]) : 8000);
	}
}
