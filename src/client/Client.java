package client;

import shared.Room;
import shared.User;
import shared.Video;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.ClientInterface;
import shared.interfaces.SubserverInterface;

import javax.security.auth.login.LoginException;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

public class Client extends UnicastRemoteObject implements ClientInterface {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
	}
	
	private final transient String centralHost;
	private final transient int centralPort;
	
	String username;
	
	transient SubserverInterface subserver;
	
	private final transient ClientGUI gui = new ClientGUI(this);
	
	private final transient ArrayList<String> requestedVideos = new ArrayList<>();
	
	private Client(String centralHost, int centralPort) throws RemoteException {
		this.centralHost = centralHost;
		this.centralPort = centralPort;
	}
	
	
	// TODO
	//  testiraj cleanup,
	//  sinhronizuj centralni server,
	//  proveri metodu download da ne uploaduje u main threadu,
	//  dodaj scrollbar na listu usera, vracanje na login screen,
	//  rano obavestavanje pri promeni stanja,
	//  disablovanje create Room dugmeta,
	//  brisanje stvari koje ne postoje,
	public void loggedIn(String username) {
		// CLEANUP
		Path path = Path.of("uploads/client/" + username + "/");
		
		if (Files.exists(path)) {
			File[] files = (new File(path.toUri())).listFiles();
			
			if (files != null) for (File file : files) file.delete();
		}
		// CLEANUP
		
		gui.setTitle("KDP Project [" + username + "]");
		
		gui.tabbedPane.removeAll();
		gui.tabbedPane.add(gui.browsePanel, "Browse");
		gui.tabbedPane.add(gui.watchPanel, "Watch");
	}
	
	public void signIn(String username, String password, boolean registration) throws RemoteException, LoginException, NotBoundException {
		CentralServerInterface central = (CentralServerInterface) LocateRegistry.getRegistry(centralHost, centralPort).lookup("/Central");
		
		if (registration) central.register(new User(this, username, password));
		else central.login(new User(this, username, password));
	}
	
	public void syncVideos() throws RemoteException {
		ArrayList<String> videoNames = subserver.getAllVideoNames();
		
		File[] files = (new File("uploads/client/" + username)).listFiles();
		
		if (files != null) for (File file : files) videoNames.remove(file.getName());
		for (String request : requestedVideos) videoNames.remove(request);
		
		for (String videoName : videoNames) {
			requestedVideos.add(videoName);
			
			try {
				subserver.requestVideo(videoName, username);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (LoginException e) {
				gui.addNotification(e.getMessage());
			}
		}
	}
	
	public void syncUsers() throws RemoteException {
		ArrayList<String> users = subserver.getUsers();
		
		for (Component user : gui.usersPanel.getComponents()) if (user instanceof JCheckBox checkBox) users.remove(checkBox.getText());
		
		for (String user : users) if (!user.equals(username)) addUser(user);
	}
	
	public void syncRooms() throws RemoteException {
		ArrayList<Room> rooms = subserver.getRooms(username);
		
		for (Room room : rooms) if (((DefaultComboBoxModel<?>) gui.rooms.getModel()).getIndexOf(room) == -1) addRoom(room);
	}
	
	@Override
	public void download(Video video) throws RemoteException {
		try {
			Files.createDirectories(Path.of("uploads/client/" + username));
			
			try (OutputStream os = new FileOutputStream("uploads/client/" + username + "/" + video, true)) {
				os.write(video.data, 0, video.size);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void finalizeVideo(String video) throws RemoteException {
		gui.addNotification("Downloaded video '" + video + "'");
		gui.addVideo(video);
	}
	
	@Override
	public void addRoom(Room room) throws RemoteException {
		gui.addRoom(room);
	}
	
	@Override
	public synchronized void addUser(String user) throws RemoteException {
		gui.addUser(user);
	}
	
	@Override
	public synchronized void assignSubserver(SubserverInterface subserver) throws RemoteException {
		if (this.subserver == null) {
			this.subserver = subserver;
			
			(new Thread(() -> {
				while (true) {
					try {
						
						
						gui.labelConnection.setText("STATUS: Connected");
						gui.labelConnection.setForeground(Color.GREEN);
						gui.uploadButton.setEnabled(true);
						
						syncVideos();
						syncUsers();
						syncRooms();
						
						try {
							Thread.sleep(7000);
						} catch (InterruptedException ignored) {
						}
						
					} catch (RemoteException e) {
						gui.labelConnection.setText("STATUS: Disconnected");
						gui.labelConnection.setForeground(Color.RED);
						gui.uploadButton.setEnabled(false);
						
						try {
							Thread.sleep(5000);
						} catch (InterruptedException ignored) {
						}
					}
				}
			})).start();
		} else this.subserver = subserver;
	}
	
	public static void main(String[] args) throws RemoteException {
		new Client(args[0], Integer.parseInt(args[1]));
	}
}
