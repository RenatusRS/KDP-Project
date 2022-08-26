package subserver;

import shared.*;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.ClientInterface;
import shared.interfaces.SubserverInterface;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Subserver extends UnicastRemoteObject implements SubserverInterface, Serializable {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
	}
	
	private CentralServerInterface server;
	
	private final HashMap<String, ClientData> users = new HashMap<>();
	
	private int id;
	
	private final Logger log;
	
	private long wakeupTime = 0;
	
	private final HashMap<String, Video> videos = new HashMap<>();
	private final Set<String> requestedVideos = ConcurrentHashMap.newKeySet();
	
	public Subserver(String centralHost, int centralPort, boolean nogui) throws RemoteException {
		log = new Logger(nogui ? System.out::print : (new SubserverGUI(id))::print);
		
		log.info("Subserver started");
		
		(new Thread(() -> {
			log.info("Connecting to Central server");
			
			try {
				while (true) {
					try {
						server = (CentralServerInterface) LocateRegistry.getRegistry(centralHost, centralPort).lookup("/Central");
						server.addSubserver(new SubserverData(this, id, wakeupTime));
						
						log.info("Connected to the Central server [" + id + ", " + wakeupTime + "]");
						
						Video.setDestination("uploads/subserver/" + id + "/");
						
						while (true) {
							log.info("Checking for new videos");
							ArrayList<String> videoNames = server.getAllVideoNames();
							for (Video video : videos.values()) if (video.finished) videoNames.remove(video.name);
							
							for (String videoName : videoNames) {
								try {
									log.info("Requesting video " + videoName);
									server.requestVideoFromCentral(videoName, id);
									Files.deleteIfExists(Path.of("uploads/subserver/" + id + "/" + videoName));
									videos.put(videoName, new Video(videoName, null));
								} catch (LoginException e) {
									log.info(e.getMessage());
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
							
							Utils.sleep(7000);
						}
					} catch (RemoteException e) {
						log.info("Failed connecting to Central server, reattempting in 2 seconds");
						Utils.sleep(2000);
					}
				}
			} catch (NotBoundException e) {
				e.printStackTrace();
			}
		})).start();
	}
	
	@Override
	public void setId(int id, long wakeupTime) throws RemoteException {
		this.id = id;
		this.wakeupTime = wakeupTime;
	}
	
	@Override
	public synchronized void assignUser(ClientData client) throws RemoteException {
		log.info("ClientData '" + client.username + "' has been " + (users.containsKey(client.username) ? "reassigned" : "assigned"));
		
		users.put(client.username, client);
		(new Thread(() -> {
			int count = 10;
			
			try {
				while (count-- != 0) {
					try {
						client.client.assignSubserver(this, client.username, wakeupTime);
					} catch (RemoteException e) {
						log.info("Failed connecting to client " + client.username + ", reattempting " + count + " more times.");
					}
				}
			} catch (LoginException ex) {
				log.info(ex.getMessage());
			}
		})).start();
	}
	
	@Override
	public void uplaodVideoDataToCentral(String video, Data data, String owner) throws RemoteException, LoginException {
		log.info("Uploading data for video '" + video + "' by user '" + owner + "'");
		server.uploadVideoDataToCentral(video, data, owner);
	}
	
	@Override
	public void uploadCentralToSubserver(String video, Data data) throws RemoteException {
		log.info("Central server sending data '" + video + "'");
		videos.get(video).write(data);
	}
	
	@Override
	public void finalizeVideoFromCentral(String video) throws RemoteException {
		log.info("Finalized video '" + video + "'");
		videos.get(video).finished = true;
	}
	
	@Override
	public Room getRoomData(int room) throws RemoteException {
		log.info("Receiving updated room data for room " + room);
		return server.getRoomData(room);
	}
	
	@Override
	public void setRoomData(int room, long time, boolean paused) throws RemoteException {
		log.info("Sending updated room data for room " + room + " [" + time + ", " + paused + "]");
		server.setRoomData(room, time, paused);
	}
	
	@Override
	public void createRoom(Room room) throws RemoteException {
		log.info("Sending request for room creation " + room);
		server.createRoom(room);
	}
	
	@Override
	public long status() throws RemoteException {
		log.info("Status check");
		return wakeupTime;
	}
	
	@Override
	public ArrayList<String> getAllVideoNames() throws RemoteException {
		log.info("Client requested all video names");
		
		ArrayList<String> names = new ArrayList<>();
		for (Video video : videos.values()) if (video.finished) names.add(video.name);
		
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
	public void requestVideoFromSubserver(String video, String username) throws RemoteException, LoginException {
		log.info("ClientData '" + username + "' requested video '" + video + "'");
		
		if (requestedVideos.contains(video + username))
			log.error("Subserver already sending video '" + video + "' to client '" + username + "'", "Video '" + video + "' is already being sent");
		
		(new Thread(() -> {
			Video videoFile = videos.get(video);
			ClientInterface client = users.get(username).client;
			
			try (InputStream is = videoFile.read()) {
				requestedVideos.add(video + username);
				
				int readBytes;
				byte[] b = new byte[1024 * 1024 * 32];
				
				while ((readBytes = is.read(b)) != -1) client.uploadSubserverToClient(videoFile.name, new Data(b, readBytes));
				
				client.finalizeVideo(videoFile.name);
			} catch (Exception e) {
				log.info("Failed sending video '" + video + "' to client '" + username + "'");
				requestedVideos.remove(video + username);
			}
		})).start();
	}
	
	@Override
	public void clearData() throws RemoteException {
		users.clear();
		videos.clear();
	}
	
	@Override
	public boolean videoNotExist(String video, String owner) throws RemoteException {
		log.info("Checking if video name not already in use '" + video + "' for user '" + owner + "'");
		return server.videoNotExist(video, owner);
	}
	
	@Override
	public void finalizeVideo(String video, String owner) throws RemoteException, LoginException {
		log.info("Finalizing video on central server '" + video + "'");
		server.finalizeVideoOnCentral(video, owner);
	}
	
	public static void main(String[] args) throws RemoteException {
		new Subserver(args.length > 0 ? args[0] : "localhost", args.length > 1 ? Integer.parseInt(args[1]) : 8000, args.length > 2 && args[2].equals("nogui"));
	}
}
