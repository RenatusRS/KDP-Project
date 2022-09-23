package server;

import shared.Data;
import shared.Room;
import shared.Utils;
import shared.Video;
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
		System.setProperty("sun.rmi.transport.tcp.responseTimeout", Utils.TIMEOUT);
	}
	
	private CentralServerInterface server;
	
	private int id;
	private long wakeupTime = 0;
	
	private final Logger log;
	
	private final HashMap<String, Video> videos = new HashMap<>();
	private final ConcurrentHashMap<String, Thread> requestedVideos = new ConcurrentHashMap<>();
	
	public Subserver(String centralHost, int centralPort, boolean nogui) throws RemoteException {
		log = new Logger(nogui ? System.out::print : (new ServerGUI("Subserver [" + centralHost + ":" + centralPort + "]", "subserver"))::print);
		
		log.info("Searching for central server on " + centralHost + ":" + centralPort);
		getSyncThread(centralHost, centralPort).start();
	}
	
	private Thread getSyncThread(String centralHost, int centralPort) {
		return new Thread(() -> {
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
							
							Utils.sleep(3000);
						}
					} catch (IOException e) {
						log.info("Failed connecting to Central server, reattempting in 2 seconds");
						Utils.sleep(2000);
					}
				}
			} catch (NotBoundException e) {
				e.printStackTrace();
			}
		});
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
		
		for (ClientData client : users) getUserConnectionThread(client).start();
	}
	
	private Thread getUserConnectionThread(ClientData client) {
		return new Thread(() -> {
			try {
				log.info("Trying to assign to user '" + client.username + "'");
				client.client.assignSubserver(this, client.username, wakeupTime, id);
				log.info("User '" + client.username + "' has been assigned");
			} catch (IOException e) {
				log.info("Failed connecting to user '" + client.username + "'");
			} catch (LoginException e) {
				log.info(e.getMessage());
			}
		});
	}
	
	@Override
	public void setId(int id, long wakeupTime) {
		log.info("ID set to '" + id + "', wake time set to '" + wakeupTime + "'");
		
		this.id = id;
		this.wakeupTime = wakeupTime;
		
		videos.clear();
		for (Thread requestedThread : requestedVideos.values()) requestedThread.interrupt();
	}
	
	@Override
	public void uploadVideoDataToCentral(String video, Data data, String owner) throws LoginException {
		log.info("Uploading data for video '" + video + "' by user '" + owner + "'");
		
		try {
			server.uploadVideoDataToCentral(video, data, owner);
		} catch (IOException e) {
			log.error("Error uploading video '" + video + "' by user '" + owner + "' to the central server", "Error uploading video to the central server");
		}
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
		log.info("Receiving updated room data for room '" + room + "'");
		try {
			return server.getRoomData(room);
		} catch (IOException e) {
			log.error("No connection to the central server while getting data for room '" + room + "'", "Couldn't get the room data from central server!");
		}
		
		return null;
	}
	
	@Override
	public void setRoomData(int room, long time, boolean paused) throws LoginException {
		log.info("Sending updated room data for room " + room + " [" + time + ", " + paused + "]");
		try {
			server.setRoomData(room, time, paused);
		} catch (IOException e) {
			log.error("No connection to the central server while getting data for room '" + room + "'", "Couldn't get the room data to central server!");
		}
	}
	
	@Override
	public void createRoom(Room room) throws LoginException {
		log.info("Sending request for room creation " + room);
		try {
			server.createRoom(room);
		} catch (IOException e) {
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
		log.info("User '" + username + "' requested all rooms he is a part of");
		try {
			return server.getRooms(username);
		} catch (IOException e) {
			log.error("No connection to the central server while getting rooms for user '" + username + "'", "Couldn't get rooms from central server!");
		}
		
		return null;
	}
	
	@Override
	public ArrayList<String> getUsers() throws LoginException {
		log.info("User requested all users");
		
		try {
			return server.getUsers();
		} catch (IOException e) {
			log.error("No connection to the central server while getting all users", "Couldn't get users from central server!");
		}
		
		return null;
	}
	
	@Override
	public void requestVideoFromSubserver(String video, ClientData client) throws LoginException {
		log.info("User '" + client.username + "' requested video '" + video + "'");
		
		if (requestedVideos.containsKey(video + client.username))
			log.error("Subserver already sending video '" + video + "' to client '" + client.username + "'", "Video '" + video + "' is already being sent");
		
		Thread thread = getUploadThread(video, client);
		requestedVideos.put(video + client.username, thread);
		thread.start();
	}
	
	private Thread getUploadThread(String video, ClientData client) {
		return new Thread(() -> {
			Video videoFile = videos.get(video);
			
			long total = videoFile.size();
			long uploaded = 0;
			try (InputStream is = videoFile.read()) {
				int readBytes;
				byte[] b = new byte[Utils.PACKAGE_SIZE];
				
				while (!Thread.currentThread().isInterrupted() && (readBytes = is.read(b)) != -1) {
					uploaded += readBytes;
					log.info("Sending data for video '" + videoFile.name + "' to user '" + client.username + "' [" + uploaded + "/" + total + ", " + videoFile.percent(uploaded) + "%]");
					
					client.client.uploadSubserverToClient(videoFile.name, new Data(b, readBytes));
				}
				
				if (!Thread.currentThread().isInterrupted()) client.client.finalizeVideo(videoFile.name);
			} catch (IOException e) {
				log.info("Failed sending video '" + video + "' to client '" + client.username + "'");
			}
			
			requestedVideos.remove(video + client.username);
		});
	}
	
	@Override
	public boolean reserveVideo(String video, String owner) throws LoginException {
		log.info("Checking if video name not already in use '" + video + "' for user '" + owner + "'");
		
		try {
			return server.reserveVideo(video, owner);
		} catch (IOException e) {
			log.error("No connection to the central server while getting video reservation for video '" + video + "' for user '" + owner + "'", "Couldn't get video reservation from central server!");
		}
		
		return false;
	}
	
	@Override
	public void finalizeVideo(String video, String owner) throws LoginException {
		log.info("Finalizing video on central server '" + video + "'");
		try {
			server.finalizeVideoOnCentral(video, owner);
		} catch (IOException e) {
			log.error("No connection to the central server while finalizing video '" + video + "' for user '" + owner + "'", "Couldn't get video finalization to central server!");
		}
	}
	
	public static void main(String[] args) throws RemoteException {
		new Subserver(args.length > 0 ? args[0] : "localhost", args.length > 1 ? Integer.parseInt(args[1]) : 8000, args.length > 2 && args[2].equals("nogui"));
	}
}
