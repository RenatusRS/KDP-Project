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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CentralServer implements CentralServerInterface {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
		System.setProperty("sun.rmi.transport.tcp.responseTimeout", "7000");
	}
	
	private final long wakeupTime = System.currentTimeMillis();
	
	private final ConcurrentHashMap<String, ClientData> unassignedUsers = new ConcurrentHashMap<>();
	private final Lock userLock = new ReentrantLock(true);
	
	private final CopyOnWriteArrayList<Room> rooms = new CopyOnWriteArrayList<>();
	private final AtomicInteger roomID = new AtomicInteger(1);
	
	private final ConcurrentHashMap<Integer, SubserverData> subservers = new ConcurrentHashMap<>();
	private final AtomicInteger subserverID = new AtomicInteger(0);
	private final ReadWriteLock removalLock = new ReentrantReadWriteLock(true);
	private final Lock reconnectLock = new ReentrantLock(true);
	
	private final ConcurrentHashMap<String, Video> videos = new ConcurrentHashMap<>();
	
	private final Set<String> requestedVideos = ConcurrentHashMap.newKeySet();
	
	private final Logger log;
	
	public CentralServer(int port, boolean nogui) throws UnknownHostException, RemoteException {
		Video.setDestination("uploads/server/");
		
		String ip = InetAddress.getLocalHost().getHostAddress();
		log = new Logger(nogui ? System.out::print : (new ServerGUI("Central Server [" + ip + ":" + port + ", " + wakeupTime + "]", "centralserver"))::print);
		
		LocateRegistry.createRegistry(port).rebind("/Central", UnicastRemoteObject.exportObject(this, 0));
		log.info("Server started on " + ip + ":" + port + " with ID " + wakeupTime);
	}
	
	private void insert(ClientData client) {
		ArrayList<SubserverData> subserversList = new ArrayList<>(subservers.values());
		
		if (!subserversList.isEmpty()) {
			subserversList.sort(Comparator.comparing(a -> a.users.size()));
			subserversList.get(0).users.put(client.username, client);
			log.info("Inserted user '" + client.username + "' into server " + subserversList.get(0).id);
		} else {
			unassignedUsers.put(client.username, client);
			log.info("Inserted user '" + client.username + "' into unassigned list");
		}
	}
	
	@Override
	public synchronized void addSubserver(SubserverData server) {
		try {
			reconnectLock.lock();
			SubserverData temp = subservers.get(server.id);
			if (temp == null || server.wakeupTime != wakeupTime) {
				server.id = subserverID.getAndIncrement();
				
				server.server.setId(server.id, wakeupTime);
				
				subservers.put(server.id, server);
				
				log.info(server + " Added subserver");
			} else {
				temp.thread.interrupt();
				temp.server = server.server;
				
				server = temp;
				
				log.info(server + " Reconnected with a subserver");
			}
			reconnectLock.unlock();
			
			server.thread = getServerStatusThread(server);
			server.thread.start();
			
			userLock.lock();
			if (!unassignedUsers.isEmpty()) {
				for (ClientData user : unassignedUsers.values()) insert(user);
				unassignedUsers.clear();
			}
			userLock.unlock();
		} catch (IOException e) {
			log.info("Lost connection to subserver during its insertion");
		}
	}
	
	private Thread getServerStatusThread(SubserverData subserver) {
		return new Thread(() -> {
			int period = 16000;
			int count = 1;
			
			try {
				while (!subserver.thread.isInterrupted() && count <= 3) {
					log.info("Contacting subserver " + subserver + " in " + period / 1000 + " seconds, attempt [" + count + "/3]");
					Thread.sleep(period);
					
					try {
						subserver.server.status();
						period = 16000;
						count = 1;
					} catch (IOException e) {
						period /= 2;
						count++;
					}
				}
				
				reconnectLock.lock();
				if (!Thread.interrupted()) {
					log.info("Subserver " + subserver + " has not responded 3 times, deleting");
					
					removalLock.writeLock().lock();
					userLock.lock();
					
					log.info("Assigning " + subserver + " users a new subserver");
					Collection<ClientData> clients = subservers.remove(subserver.id).users.values();
					for (ClientData client : clients) insert(client);
					
					userLock.unlock();
					removalLock.writeLock().unlock();
				}
				reconnectLock.unlock();
				
			} catch (InterruptedException ignored) {
			}
		});
	}
	
	@Override
	public synchronized long register(ClientData client) throws LoginException {
		removalLock.readLock().lock();
		if (unassignedUsers.containsKey(client.username)) {
			removalLock.readLock().unlock();
			log.error("Username " + client + " is already taken", "Username is taken!");
		}
		
		for (SubserverData server : subservers.values())
			if (server.users.containsKey(client.username)) {
				removalLock.readLock().unlock();
				log.error("Username " + client + " is already taken", "Username is taken!");
			}
		
		log.info("User " + client + " registred");
		
		userLock.lock();
		insert(client);
		userLock.unlock();
		
		removalLock.readLock().unlock();
		
		return wakeupTime;
	}
	
	@Override
	public long login(ClientData client) throws LoginException {
		removalLock.readLock().lock();
		for (SubserverData server : subservers.values()) {
			if (server.users.containsKey(client.username)) {
				if (!server.users.get(client.username).password.equals(client.password)) {
					removalLock.readLock().unlock();
					log.error("Bad password for " + client + ", attempted: '" + client.password + "', real: " + server.users.get(client.username), "Wrong password!");
				}
				
				server.users.put(client.username, client);
				log.info("User " + client + " logged in to server " + server);
				
				removalLock.readLock().unlock();
				return wakeupTime;
			}
		}
		
		if (unassignedUsers.containsKey(client.username)) {
			if (!unassignedUsers.get(client.username).password.equals(client.password)) {
				removalLock.readLock().unlock();
				log.error("Bad password for " + client + ", attempted: '" + client.password + "', real: " + unassignedUsers.get(client.username), "Wrong password!");
			}
			
			log.info("User " + client + " logged in, but no subserver to assign to yet");
		} else {
			removalLock.readLock().unlock();
			log.error("User " + client + " doesn't exist", "User doesn't exist!");
		}
		removalLock.readLock().unlock();
		
		return wakeupTime;
	}
	
	@Override
	public boolean reserveVideo(String video, String owner) {
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
	public void uploadVideoDataToCentral(String video, Data data, String username) throws LoginException {
		log.info("Recieved request to upload data '" + video + "' by user '" + username + "'");
		
		synchronized (video.intern()) {
			Video videoFile = videos.get(video);
			
			if (!username.equals(videoFile.owner))
				log.error("Previous owner '" + username + "' tried to upload video '" + video + "' which is now owned by '" + videoFile.owner + "'", "Video is now owned by someone else!");
			
			videoFile.write(data);
		}
	}
	
	@Override
	public void finalizeVideoOnCentral(String video, String username) throws LoginException {
		log.info("Recieved request to finalize '" + video + "' by user '" + username + "'");
		
		synchronized (video.intern()) {
			Video videoFile = videos.get(video);
			
			if (!username.equals(videoFile.owner))
				log.error("Previous owner '" + username + "' tried to finalize video '" + video + "' which is now owned by '" + videoFile.owner + "'", "Video is now owned by someone else!");
			
			videoFile.finished = true;
		}
	}
	
	@Override
	public void requestVideoFromCentral(String video, int subserverID) throws LoginException {
		log.info("Subserver " + subserverID + " requested video '" + video + "'");
		
		if (requestedVideos.contains(video + subserverID))
			log.error("Central server already sending video '" + video + "' to subserver " + subserverID, "Video '" + video + "' is already being sent by the central server");
		
		requestedVideos.add(video + subserverID);
		getUploadThread(video, subserverID).start();
	}
	
	private Thread getUploadThread(String video, int subserverID) {
		return new Thread(() -> {
			Video videoFile = videos.get(video);
			SubserverInterface subserver = subservers.get(subserverID).server;
			
			long total = videoFile.size();
			long uploaded = 0;
			try (InputStream is = videoFile.read()) {
				int readBytes;
				byte[] b = new byte[Utils.PACKAGE_SIZE];
				
				while ((readBytes = is.read(b)) != -1) {
					uploaded += readBytes;
					log.info("Sending data for video '" + videoFile.name + "' to subserver '" + subserverID + "' [" + uploaded + "/" + total + ", " + videoFile.percent(uploaded) + "%]");
					
					subserver.uploadCentralToSubserver(videoFile.name, new Data(b, readBytes));
				}
				
				subserver.finalizeVideoFromCentral(videoFile.name);
			} catch (IOException e) {
				log.info("Failed sending video '" + video + "' to subserver " + subserverID);
				requestedVideos.remove(video + subserverID);
			}
		});
	}
	
	@Override
	public Room getRoomData(int roomID) {
		log.info("Sending updated room data for room " + roomID);
		
		Room room = rooms.get(roomID - 1);
		
		if (!room.getPaused() && room.getLastUpdate() + 3500 < System.currentTimeMillis()) {
			log.info("Unpaused room " + roomID + " not receiving updates for 4 seconds, pausing");
			room.sync(room.getTime(), true);
		}
		
		return room;
	}
	
	@Override
	public void setRoomData(int room, long time, boolean paused) {
		log.info("Receiving updated room data for room " + room + " [" + time + ", " + paused + "]");
		rooms.get(room - 1).sync(time, paused);
	}
	
	@Override
	public void createRoom(Room room) {
		room.setID(roomID.getAndIncrement());
		rooms.add(room);
		
		log.info("Received request for room creation " + room);
	}
	
	@Override
	public ArrayList<String> getUsers() {
		ArrayList<String> allUsers = new ArrayList<>(unassignedUsers.keySet());
		for (SubserverData server : subservers.values()) allUsers.addAll(server.users.keySet());
		
		log.info("Received request for all user names " + allUsers);
		return allUsers;
	}
	
	@Override
	public ArrayList<ClientData> getUsers(int id) {
		ArrayList<ClientData> list = new ArrayList<>(subservers.get(id).users.values());
		
		log.info("Subserver '" + id + "' requested all of its users " + list);
		return list;
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
	
	public static void main(String[] args) throws UnknownHostException, RemoteException {
		new CentralServer(args.length > 0 ? Integer.parseInt(args[0]) : 8000, args.length > 1 && args[1].equals("nogui"));
	}
}
