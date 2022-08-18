package server;

import shared.*;
import shared.interfaces.CentralServerInterface;
import shared.interfaces.SubserverInterface;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CentralServer implements CentralServerInterface {
	static {
		if (System.getSecurityManager() == null) System.setSecurityManager(new SecurityManager());
	}
	
	private final long wakeupTime = System.currentTimeMillis();
	
	private final HashMap<String, User> unassignedUsers = new HashMap<>();
	private final ReadWriteLock usersLock = new ReentrantReadWriteLock(true);
	
	private final ArrayList<Room> rooms = new ArrayList<>();
	private final AtomicInteger roomID = new AtomicInteger(1);
	
	private final ArrayList<SubserverData> subservers = new ArrayList<>();
	private final AtomicInteger subserverID = new AtomicInteger(0);
	private final ReadWriteLock subserversLock = new ReentrantReadWriteLock(true);
	
	private final Logger log;
	
	public CentralServer(int port, boolean nogui) {
		try {
			LocateRegistry.createRegistry(port).rebind("/Central", UnicastRemoteObject.exportObject(this, 0));
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		log = new Logger(nogui ? System.out::print : (new CentralServerGUI("host", port))::print);
		
		log.info("Server started on " + port);
	}
	
	@Override
	public synchronized SubserverData addSubserver(SubserverData server) throws RemoteException {
		if (server.wakeupTime != wakeupTime) {
			server.wakeupTime = wakeupTime;
			
			server.id = subserverID.getAndIncrement();
			
			server.server.clearUsers();
			subservers.add(server);
			
			log.info(server + " Added subserver");
		} else {
			SubserverData temp = subservers.get(server.id);
			temp.thread.interrupt();
			temp.server = server.server;
			
			for (User user : temp.users.values()) temp.server.assignUser(user);
			
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
				Collection<User> users = subservers.remove(finalServer.id).users.values();
				subserversLock.writeLock().unlock();
				
				for (User user : users) insert(user);
				
			} catch (InterruptedException ignored) {
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		});
		
		server.thread.start();
		
		if (unassignedUsers.isEmpty()) return server;
		
		usersLock.readLock().lock();
		for (User user : unassignedUsers.values()) {
			server.server.assignUser(user);
			server.users.put(user.username, user);
			
			log.info(server + " Assigned user " + user);
		}
		
		unassignedUsers.clear();
		usersLock.readLock().unlock();
		
		return server;
	}
	
	@Override
	public synchronized void register(User user) throws RemoteException, LoginException {
		if (unassignedUsers.containsKey(user.username)) log.error(" Username " + user + " is already taken", "Username is taken!");
		
		for (SubserverData server : subservers)
			if (server.users.containsKey(user.username)) log.error(" Username " + user + " is already taken", "Username is taken!");
		
		log.info(" User " + user + " registred");
		
		insert(user);
	}
	
	private void insert(User user) throws RemoteException {
		int min = Integer.MAX_VALUE;
		SubserverData leastPopulated = null;
		
		for (SubserverData server : subservers) {
			if (server.users.size() < min) {
				min = server.users.size();
				leastPopulated = server;
			}
		}
		
		if (leastPopulated == null) {
			unassignedUsers.put(user.username, user);
			log.info(" User " + user + " put in unassigned list");
		} else {
			leastPopulated.users.put(user.username, user);
			leastPopulated.server.assignUser(user);
			log.info(" User " + user + " registred put in server " + leastPopulated);
		}
	}
	
	@Override
	public void login(User user) throws RemoteException, LoginException {
		for (SubserverData server : subservers) {
			if (server.users.containsKey(user.username)) {
				if (!server.server.verifyCredentials(user.username, user.password))
					log.error(" Bad password for " + user + ", attempted: '" + user.password + "', real: " + server.users.get(user.username), "Wrong password!");
				
				server.users.put(user.username, user);
				server.server.assignUser(user);
				log.info(" User " + user + " logged in to server " + server);
				return;
			}
		}
		
		if (unassignedUsers.containsKey(user.username)) log.info(" User " + user + " logged in, but no subserver to assign to yet");
		else log.error("User " + user + " doesn't exist", "User doesn't exist!");
	}
	
	@Override
	public boolean validateName(String video) throws RemoteException {
		log.info("Recieved request to upload video '" + video + "'");
		
		try {
			Files.createDirectories(Path.of("uploads/server/"));
			
			Path path = Path.of("uploads/server/" + video);
			Path pathTemp = Path.of("uploads/server/" + video + ".temp");
			
			synchronized (video.intern()) {
				if (Files.exists(path) || Files.exists(pathTemp)) {
					log.info("Video with name '" + video + "' already exists");
					return false;
				}
			}
			
			Files.createFile(pathTemp);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	@Override
	public void addVideo(Video video) throws RemoteException {
		log.info("Recieved request to upload video '" + video + "'");
		
		try (OutputStream os = new FileOutputStream("uploads/server/" + video + ".temp", true)) {
			os.write(video.data, 0, video.size);
			log.info("Uploaded video '" + video + "'");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void finalizeVideo(String video) throws RemoteException {
		new File("uploads/server/" + video + ".temp").renameTo(new File("uploads/server/" + video));
	}
	
	@Override
	public void getVideo(String video, int subserverID) throws RemoteException {
		log.info("Subserver " + subserverID + " requested video '" + video + "'");
		File file = new File("uploads/server/" + video);
		
		SubserverInterface subserver = subservers.get(subserverID).server;
		
		try (InputStream is = new FileInputStream(file)) {
			int readBytes;
			byte[] b = new byte[1024 * 1024 * 32];
			
			while ((readBytes = is.read(b)) != -1) subserver.getVideo(new Video(file.getName(), b, readBytes));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		subserver.finalizeVideoSubserver(file.getName());
	}
	
	@Override
	public Room getRoom(int roomID) throws RemoteException {
		log.info("Sending updated room data for room " + roomID);
		
		Room room = rooms.get(roomID - 1);
		
		if (!room.getPaused() && room.getLastUpdate() + 3500 < System.currentTimeMillis()) {
			log.info("Unpaused room " + roomID + " not receiving updates for 4 seconds, pausing");
			room.sync(room.getTime(), true);
		}
		
		return room;
	}
	
	@Override
	public void syncRoom(int room, long time, boolean paused) throws RemoteException {
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
		else for (SubserverData server : subservers) allUsers.addAll(server.users.keySet());
		
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
		File[] files = (new File("uploads/server/")).listFiles();
		
		ArrayList<String> names = new ArrayList<>();
		if (files != null) for (File file : files) {
			String name = file.getName();
			
			if (!name.substring(name.lastIndexOf(".")).equals(".temp")) names.add(file.getName());
		}
		
		log.info("Received request for all video names " + names);
		
		return names;
	}
	
	
	public static void main(String[] args) {
		Path path = Path.of("uploads/server/");
		
		if (Files.exists(path)) {
			File[] files = (new File("uploads/server/")).listFiles();
			
			if (files != null) for (File file : files) file.delete();
		}
		
		new CentralServer(Integer.parseInt(args[0]), args.length > 1 && args[1].equals("nogui"));
	}
}
