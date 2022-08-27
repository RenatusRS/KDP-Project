package subserver;

import shared.*;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.SubserverInterface;
import shared.remote.ClientData;
import shared.remote.SubserverData;

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
import java.util.concurrent.ConcurrentHashMap;

public class Subserver extends UnicastRemoteObject implements SubserverInterface, Serializable {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
	}
	
	private CentralServerInterface server;
	
	private int id;
	
	private final Logger log;
	
	private long wakeupTime = 0;
	
	private final HashMap<String, Video> videos = new HashMap<>();
	private final ConcurrentHashMap<String, Thread> requestedVideos = new ConcurrentHashMap<>();
	private final HashMap<String, Thread> threads = new HashMap<>();
	
	public Subserver(String centralHost, int centralPort, boolean nogui) throws RemoteException {
		log = new Logger(nogui ? System.out::print : (new SubserverGUI())::print);
		
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
							syncVideos();
							syncUsers();
							
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
	
	private void syncVideos() throws RemoteException {
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
	}
	
	private void syncUsers() throws RemoteException {
		log.info("Checking for my users");
		ArrayList<ClientData> users = server.getUsers(id);
		
		for (ClientData client : users) {
			if (threads.containsKey(client.username) && threads.get(client.username).isAlive()) {
				log.info("Connection attempt to '" + client.username + "' is still running, skipping");
				continue;
			}
			
			Thread thread = new Thread(() -> {
				try {
					log.info("Trying to assign to user '" + client.username + "'");
					client.client.assignSubserver(this, client.username, wakeupTime);
					log.info("User '" + client.username + "' has been assigned");
				} catch (RemoteException e) {
					log.info("Failed connecting to user " + client.username);
				} catch (LoginException e) {
					log.info(e.getMessage());
				}
			});
			
			threads.put(client.username, thread);
			thread.start();
		}
	}
	
	@Override
	public void setId(int id, long wakeupTime) throws RemoteException {
		log.info("ID set to '" + id + "', wake time set to '" + wakeupTime + "'");
		
		this.id = id;
		this.wakeupTime = wakeupTime;
		
		videos.clear();
		for (Thread requestedThread : requestedVideos.values()) requestedThread.interrupt();
	}
	
	@Override
	public void uploadVideoDataToCentral(String video, Data data, String owner) throws LoginException, RemoteException {
		log.info("Uploading data for video '" + video + "' by user '" + owner + "'");
		
		server.uploadVideoDataToCentral(video, data, owner);
	}
	
	@Override
	public void uploadCentralToSubserver(String video, Data data) {
		log.info("Central server sending data '" + video + "'");
		videos.get(video).write(data);
	}
	
	@Override
	public void finalizeVideoFromCentral(String video) {
		log.info("Finalized video '" + video + "'");
		videos.get(video).finished = true;
	}
	
	@Override
	public Room getRoomData(int room) throws LoginException {
		log.info("Receiving updated room data for room " + room);
		try {
			return server.getRoomData(room);
		} catch (RemoteException e) {
			log.error("No connection to the central server while getting data for room '" + room + "'", "Couldn't get the room data from central server!");
		}
		
		return null;
	}
	
	@Override
	public void setRoomData(int room, long time, boolean paused) throws LoginException {
		log.info("Sending updated room data for room " + room + " [" + time + ", " + paused + "]");
		try {
			server.setRoomData(room, time, paused);
		} catch (RemoteException e) {
			log.error("No connection to the central server while getting data for room '" + room + "'", "Couldn't get the room data to central server!");
		}
	}
	
	@Override
	public void createRoom(Room room) throws LoginException {
		log.info("Sending request for room creation " + room);
		try {
			server.createRoom(room);
		} catch (RemoteException e) {
			log.error("No connection to the central server while creaing room '" + room + "'", "Couldn't get the room to central server!");
		}
	}
	
	@Override
	public long status() {
		log.info("Status check");
		return wakeupTime;
	}
	
	@Override
	public ArrayList<String> getAllVideoNames() {
		log.info("User requested all video names");
		
		ArrayList<String> names = new ArrayList<>();
		for (Video video : videos.values()) if (video.finished) names.add(video.name);
		
		log.info("Received request for all video names " + names);
		
		return names;
	}
	
	@Override
	public ArrayList<Room> getRooms(String username) throws LoginException {
		log.info("User " + username + " requested all rooms he is a part of");
		try {
			return server.getRooms(username);
		} catch (RemoteException e) {
			log.error("No connection to the central server while getting rooms for user '" + username + "'", "Couldn't get rooms from central server!");
		}
		
		return null;
	}
	
	@Override
	public ArrayList<String> getUsers() throws LoginException {
		log.info("User requested all users");
		
		try {
			return server.getUsers();
		} catch (RemoteException e) {
			log.error("No connection to the central server while getting all users", "Couldn't get users from central server!");
		}
		
		return null;
	}
	
	@Override
	public void requestVideoFromSubserver(String video, ClientData client) throws LoginException {
		log.info("User '" + client.username + "' requested video '" + video + "'");
		
		if (requestedVideos.containsKey(video + client.username))
			log.error("Subserver already sending video '" + video + "' to client '" + client.username + "'", "Video '" + video + "' is already being sent");
		
		Thread thread = new Thread(() -> {
			Video videoFile = videos.get(video);
			
			try (InputStream is = videoFile.read()) {
				int readBytes;
				byte[] b = new byte[1024 * 1024 * 32];
				
				while (!requestedVideos.get(video + client.username).isInterrupted() && (readBytes = is.read(b)) != -1) {
					log.info("Sending data for video '" + videoFile.name + "' to user '" + client.username + "'");
					client.client.uploadSubserverToClient(videoFile.name, new Data(b, readBytes));
				}
				if (!Thread.interrupted()) client.client.finalizeVideo(videoFile.name);
			} catch (Exception e) {
				log.info("Failed sending video '" + video + "' to client '" + client.username + "'");
			}
			
			requestedVideos.remove(video + client.username);
		});
		
		requestedVideos.put(video + client.username, thread);
		
		thread.start();
	}
	
	@Override
	public boolean reserveVideo(String video, String owner) throws LoginException {
		log.info("Checking if video name not already in use '" + video + "' for user '" + owner + "'");
		
		try {
			return server.reserveVideo(video, owner);
		} catch (RemoteException e) {
			log.error("No connection to the central server while getting video reservation for video '" + video + "' for user '" + owner + "'", "Couldn't get video reservation from central server!");
		}
		
		return false;
	}
	
	@Override
	public void finalizeVideo(String video, String owner) throws LoginException {
		log.info("Finalizing video on central server '" + video + "'");
		try {
			server.finalizeVideoOnCentral(video, owner);
		} catch (RemoteException e) {
			log.error("No connection to the central server while finalizing video '" + video + "' for user '" + owner + "'", "Couldn't get video finalization to central server!");
		}
	}
	
	public static void main(String[] args) throws RemoteException {
		new Subserver(args.length > 0 ? args[0] : "localhost", args.length > 1 ? Integer.parseInt(args[1]) : 8000, args.length > 2 && args[2].equals("nogui"));
	}
}
