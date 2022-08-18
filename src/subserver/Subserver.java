package subserver;

import shared.*;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.ClientInterface;
import shared.interfaces.SubserverInterface;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

public class Subserver extends UnicastRemoteObject implements SubserverInterface, Serializable {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
	}
	
	private CentralServerInterface server;
	
	private final HashMap<String, User> users = new HashMap<>();
	
	private final ArrayList<String> requestedVideos = new ArrayList<>();
	
	private int id;
	
	private final Logger log;
	
	public Subserver(String centralHost, int centralPort, boolean nogui) throws RemoteException {
		log = new Logger(nogui ? System.out::print : (new SubserverGUI(id))::print);
		
		log.info("Subserver started");
		
		(new Thread(() -> {
			log.info("Connecting to Central server");
			SubserverData me;
			long wakeupTime = 0;
			
			try {
				while (true) {
					try {
						server = (CentralServerInterface) LocateRegistry.getRegistry(centralHost, centralPort).lookup("/Central");
						me = server.addSubserver(new SubserverData(this, id, wakeupTime));
						id = me.id;
						wakeupTime = me.wakeupTime;
						
						log.info("Connected to the Central server [" + id + ", " + wakeupTime + "]");
						
						// CLEANUP
						Path path = Path.of("uploads/subserver/" + id + "/");
						
						if (Files.exists(path)) {
							File[] files = (new File(path.toUri())).listFiles();
							
							if (files != null) for (File file : files) file.delete();
						}
						// CLEANUP
						
						while (true) {
							log.info("Checking for new videos");
							ArrayList<String> videoNames = server.getAllVideoNames();
							
							File[] files = (new File("uploads/subserver/" + id)).listFiles();
							
							if (files != null) for (File file : files) videoNames.remove(file.getName());
							for (String request : requestedVideos) videoNames.remove(request);
							
							for (String videoName : videoNames) {
								requestedVideos.add(videoName);
								
								try {
									Files.createDirectories(Path.of("uploads/subserver/" + id));
									
									log.info("Requesting video " + videoName);
									server.getVideo(videoName, id);
								} catch (LoginException e) {
									log.info(e.getMessage());
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
							
							try {
								Thread.sleep(7000);
							} catch (InterruptedException ignored) {
							}
						}
					} catch (RemoteException e) {
						try {
							log.info("Failed connecting to Central server, reattempting in 2 seconds");
							Thread.sleep(2000);
						} catch (InterruptedException ex) {
							ex.printStackTrace();
						}
					}
				}
			} catch (NotBoundException e) {
				e.printStackTrace();
			}
		})).start();
	}
	
	@Override
	public synchronized void assignUser(User user) throws RemoteException {
		log.info("User '" + user.username + "' has been " + (users.containsKey(user.username) ? "reassigned" : "assigned"));
		
		users.put(user.username, user);
		user.client.assignSubserver(this);
	}
	
	@Override
	public boolean verifyCredentials(String username, String password) throws RemoteException {
		boolean verification = users.get(username).password.equals(password);
		
		log.info("Verification for '" + username + "' requested:" + verification + "CORRECT '" + users.get(username).password + "' GIVEN '" + password + "'");
		return verification;
	}
	
	@Override
	public void addVideo(Video video) throws RemoteException {
		log.info("Uploading video " + video);
		server.addVideo(video);
	}
	
	@Override
	public void getVideo(Video video) throws RemoteException {
		log.info("Central server sending video '" + video + "'");
		
		try (OutputStream os = new FileOutputStream("uploads/subserver/" + id + "/" + video + ".temp", true)) {
			os.write(video.data, 0, video.size);
			
			log.info("Downloaded video '" + video + "'");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void finalizeVideoSubserver(String video) throws RemoteException {
		log.info("Finalized video '" + video + "'");
		new File("uploads/subserver/" + id + "/" + video + ".temp").renameTo(new File("uploads/subserver/" + id + "/" + video));
	}
	
	@Override
	public Room getRoomData(int room) throws RemoteException {
		log.info("Receiving updated room data for room " + room);
		return server.getRoom(room);
	}
	
	@Override
	public void setRoomData(int room, long time, boolean paused) throws RemoteException {
		log.info("Sending updated room data for room " + room + " [" + time + ", " + paused + "]");
		server.syncRoom(room, time, paused);
	}
	
	@Override
	public void createRoom(Room room) throws RemoteException {
		log.info("Sending request for room creation " + room);
		server.createRoom(room);
	}
	
	@Override
	public void status() throws RemoteException {
		log.info("Server checked status");
	}
	
	@Override
	public ArrayList<String> getAllVideoNames() throws RemoteException {
		log.info("Client requested all video names");
		
		File[] files = (new File("uploads/subserver/" + id)).listFiles();
		
		ArrayList<String> names = new ArrayList<>();
		if (files != null) for (File file : files) {
			String name = file.getName();
			
			if (!name.substring(name.lastIndexOf(".")).equals(".temp")) names.add(file.getName());
		}
		
		log.info("Received request for all video names " + names);
		
		return names;
	}
	
	@Override
	public ArrayList<Room> getRooms(String username) throws RemoteException {
		log.info("Client " + username + " requested all rooms he is a part of");
		return server.getRooms(username);
	}
	
	@Override
	public ArrayList<String> getUsers() throws RemoteException {
		log.info("Client requested all users");
		return server.getUsers();
	}
	
	@Override
	public void requestVideo(String video, String username) throws RemoteException {
		(new Thread(() -> {
			log.info("User '" + username + "' requested video '" + video + "'");
			File file = new File("uploads/subserver/" + id + "/" + video);
			
			ClientInterface client = users.get(username).client;
			
			try (InputStream is = new FileInputStream(file)) {
				int readBytes;
				byte[] b = new byte[1024 * 1024 * 32];
				
				while ((readBytes = is.read(b)) != -1) client.download(new Video(file.getName(), b, readBytes));
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			try {
				client.finalizeVideo(file.getName());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		})).start();
	}
	
	@Override
	public void clearUsers() throws RemoteException {
		users.clear();
	}
	
	@Override
	public boolean validateVideo(String video) throws RemoteException {
		log.info("Checking if video name not already in use '" + video + "'");
		return server.validateName(video);
	}
	
	@Override
	public void finalizeVideo(String video) throws RemoteException {
		log.info("Finalizing video on central server '" + video + "'");
		server.finalizeVideo(video);
	}
	
	public static void main(String[] args) throws RemoteException {
		new Subserver(args[0], Integer.parseInt(args[1]), args.length > 2 && args[2].equals("nogui"));
	}
}
