package server;

import shared.*;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.SubserverInterface;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CentralServer implements CentralServerInterface {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
	}
	
	private final long wakeupTime = System.currentTimeMillis();
	
	private final HashMap<String, ClientData> unassignedUsers = new HashMap<>();
	private final ReadWriteLock usersLock = new ReentrantReadWriteLock(true);
	
	private final ArrayList<Room> rooms = new ArrayList<>();
	private final AtomicInteger roomID = new AtomicInteger(1);
	
	private final HashMap<Integer, SubserverData> subservers = new HashMap<>();
	private final AtomicInteger subserverID = new AtomicInteger(0);
	private final ReadWriteLock subserversLock = new ReentrantReadWriteLock(true);
	
	private final HashMap<String, Video> videos = new HashMap<>();
	private final Set<String> requestedVideos = ConcurrentHashMap.newKeySet();
	
	private final Logger log;
	
	public CentralServer(int port, boolean nogui) {
		Video.setDestination("uploads/server/");
		
		log = new Logger(nogui ? System.out::print : (new CentralServerGUI(port))::print);
		
		try {
			LocateRegistry.createRegistry(port).rebind("/Central", UnicastRemoteObject.exportObject(this, 0));
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		log.info("Server started on " + port + " with ID " + wakeupTime);
	}
	
	@Override
	public synchronized void addSubserver(SubserverData server) throws RemoteException {
		if (server.wakeupTime != wakeupTime) {
			server.wakeupTime = wakeupTime;
			
			server.id = subserverID.getAndIncrement();
			
			server.server.clearData();
			subservers.put(server.id, server);
			
			server.server.setId(server.id, server.wakeupTime);
			
			log.info(server + " Added subserver");
		} else {
			SubserverData temp = subservers.get(server.id);
			temp.thread.interrupt();
			temp.server = server.server;
			
			for (ClientData client : temp.users.values()) temp.server.assignUser(client);
			
			server = temp;
			
			log.info(server + " Reconnected with a subserver");
		}
		
		SubserverData finalServer = server;
		server.thread = new Thread(() -> {
			int period = 16000;
			int count = 1;
			
			try {
				while (count <= 3) {
					log.info("Contacting subserver " + finalServer + " in " + period / 1000 + " seconds, attempt [" + count + "/3]");
					Thread.sleep(period);
					
					try {
						finalServer.server.status();
						period = 16000;
						count = 1;
					} catch (RemoteException e) {
						period /= 2;
						count++;
					}
				}
				
				log.info("Subserver " + finalServer + " has not responded 3 times, deleting");
				
				subserversLock.writeLock().lock();
				Collection<ClientData> clients = subservers.remove(finalServer.id).users.values();
				subserversLock.writeLock().unlock();
				
				for (ClientData client : clients) insert(client);
				
			} catch (InterruptedException ignored) {
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		});
		
		server.thread.start();
		
		if (unassignedUsers.isEmpty()) return;
		
		usersLock.readLock().lock();
		for (ClientData client : unassignedUsers.values()) {
			server.server.assignUser(client);
			server.users.put(client.username, client);
			
			log.info(server + " Assigned client " + client);
		}
		
		unassignedUsers.clear();
		usersLock.readLock().unlock();
	}
	
	@Override
	public synchronized long register(ClientData client) throws RemoteException, LoginException {
		if (unassignedUsers.containsKey(client.username)) log.error(" Username " + client + " is already taken", "Username is taken!");
		
		for (SubserverData server : subservers.values())
			if (server.users.containsKey(client.username)) log.error(" Username " + client + " is already taken", "Username is taken!");
		
		log.info(" ClientData " + client + " registred");
		
		insert(client);
		
		return wakeupTime;
	}
	
	private void insert(ClientData client) throws RemoteException {
		int min = Integer.MAX_VALUE;
		SubserverData leastPopulated = null;
		
		for (SubserverData server : subservers.values()) {
			if (server.users.size() < min) {
				min = server.users.size();
				leastPopulated = server;
			}
		}
		
		if (leastPopulated == null) {
			unassignedUsers.put(client.username, client);
			log.info(" ClientData " + client + " put in unassigned list");
		} else {
			leastPopulated.users.put(client.username, client);
			leastPopulated.server.assignUser(client);
			log.info(" ClientData " + client + " registred put in server " + leastPopulated);
		}
	}
	
	@Override
	public long login(ClientData client) throws RemoteException, LoginException {
		for (SubserverData server : subservers.values()) {
			if (server.users.containsKey(client.username)) {
				if (!server.users.get(client.username).password.equals(client.password))
					log.error(" Bad password for " + client + ", attempted: '" + client.password + "', real: " + server.users.get(client.username), "Wrong password!");
				
				server.users.put(client.username, client);
				server.server.assignUser(client);
				log.info(" ClientData " + client + " logged in to server " + server);
				return wakeupTime;
			}
		}
		
		if (unassignedUsers.containsKey(client.username)) log.info(" ClientData " + client + " logged in, but no subserver to assign to yet");
		else log.error("ClientData " + client + " doesn't exist", "ClientData doesn't exist!");
		
		return wakeupTime;
	}
	
	@Override
	public boolean videoNotExist(String video, String owner) throws RemoteException {
		log.info("Recieved request to upload video '" + video + "'");
		
		Video videoFile = videos.get(video);
		
		try {
			synchronized (video.intern()) {
				if (videoFile != null && !videoFile.expired() && (!videoFile.owner.equals(owner) || videoFile.finished)) {
					log.info("Video with name '" + video + "' already exists");
					return false;
				}
				
				videos.put(video, new Video(video, owner));
				Files.deleteIfExists(Path.of("uploads/server/" + video));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	@Override
	public void uploadVideoDataToCentral(String video, Data data, String username) throws RemoteException, LoginException {
		log.info("Recieved request to upload data '" + video + "' by user '" + username + "'");
		
		synchronized (video.intern()) {
			Video videoFile = videos.get(video);
			
			if (!username.equals(videoFile.owner))
				log.error("Previous owner '" + username + "' tried to upload video '" + video + "' which is now owned by '" + videoFile.owner + "'", "Video is now owned by someone else!");
			
			videoFile.write(data);
		}
	}
	
	@Override
	public void finalizeVideoOnCentral(String video, String username) throws RemoteException, LoginException {
		log.info("Recieved request to finalize '" + video + "' by user '" + username + "'");
		
		synchronized (video.intern()) {
			Video videoFile = videos.get(video);
			
			if (!username.equals(videoFile.owner))
				log.error("Previous owner '" + username + "' tried to finalize video '" + video + "' which is now owned by '" + videoFile.owner + "'", "Video is now owned by someone else!");
			
			videoFile.finished = true;
		}
	}
	
	@Override
	public void requestVideoFromCentral(String video, int subserverID) throws RemoteException, LoginException {
		log.info("Subserver " + subserverID + " requested video '" + video + "'");
		
		if (requestedVideos.contains(video + subserverID))
			log.error("Central server already sending video '" + video + "' to subserver " + subserverID, "Video '" + video + "' is already being sent by the central server");
		
		(new Thread(() -> {
			Video videoFile = videos.get(video);
			SubserverInterface subserver = subservers.get(subserverID).server;
			
			try (InputStream is = videoFile.read()) {
				requestedVideos.add(video + subserverID);
				
				int readBytes;
				byte[] b = new byte[1024 * 1024 * 32];
				
				while ((readBytes = is.read(b)) != -1) subserver.uploadCentralToSubserver(videoFile.name, new Data(b, readBytes));
				
				subserver.finalizeVideoFromCentral(videoFile.name);
			} catch (Exception e) {
				log.info("Failed sending video '" + video + "' to subserver " + subserverID);
				requestedVideos.remove(video + subserverID);
			}
		})).start();
	}
	
	@Override
	public Room getRoomData(int roomID) throws RemoteException {
		log.info("Sending updated room data for room " + roomID);
		
		Room room = rooms.get(roomID - 1);
		
		if (!room.getPaused() && room.getLastUpdate() + 3500 < System.currentTimeMillis()) {
			log.info("Unpaused room " + roomID + " not receiving updates for 4 seconds, pausing");
			room.sync(room.getTime(), true);
		}
		
		return room;
	}
	
	@Override
	public void setRoomData(int room, long time, boolean paused) throws RemoteException {
		log.info("Receiving updated room data for room " + room + " [" + time + ", " + paused + "]");
		rooms.get(room - 1).sync(time, paused);
	}
	
	@Override
	public synchronized void createRoom(Room room) throws RemoteException {
		room.setID(roomID.getAndIncrement());
		rooms.add(room);
		
		log.info("Received request for room creation " + room);
	}
	
	@Override
	public ArrayList<String> getUsers() {
		ArrayList<String> allUsers = new ArrayList<>();
		
		if (!unassignedUsers.isEmpty()) allUsers.addAll(unassignedUsers.keySet());
		else for (SubserverData server : subservers.values()) allUsers.addAll(server.users.keySet());
		
		log.info("Received request for all user names " + allUsers);
		
		return allUsers;
	}
	
	@Override
	public ArrayList<Room> getRooms(String username) {
		ArrayList<Room> allRooms = new ArrayList<>();
		
		for (Room room : rooms) if (room.viewers.contains(username)) allRooms.add(room);
		
		log.info("Received request for all rooms for user '" + username + "' " + allRooms);
		return allRooms;
	}
	
	@Override
	public ArrayList<String> getAllVideoNames() {
		ArrayList<String> names = new ArrayList<>();
		for (Video video : videos.values()) if (video.finished) names.add(video.name);
		
		log.info("Received request for all video names " + names);
		
		return names;
	}
	
	
	public static void main(String[] args) {
		new CentralServer(args.length > 0 ? Integer.parseInt(args[0]) : 8000, args.length > 1 && args[1].equals("nogui"));
	}
}
