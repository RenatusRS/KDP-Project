package shared.interfaces;

import shared.Room;
import shared.User;
import shared.Video;

import javax.security.auth.login.LoginException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface SubserverInterface extends Remote {
	public void assignUser(User user) throws RemoteException;
	
	public boolean verifyCredentials(String username, String password) throws RemoteException;
	
	public void addVideo(Video video) throws RemoteException;
	
	public void getVideo(Video video) throws RemoteException;
	
	public Room getRoomData(int room) throws RemoteException;
	
	public void setRoomData(int room, long time, boolean paused) throws RemoteException;
	
	public void createRoom(Room room) throws RemoteException;
	
	public void status() throws RemoteException;
	
	public ArrayList<String> getAllVideoNames() throws RemoteException;
	
	public ArrayList<Room> getRooms(String username) throws RemoteException;
	
	public ArrayList<String> getUsers() throws RemoteException;
	
	public void requestVideo(String video, String username) throws RemoteException, LoginException;
	
	public void clearUsers() throws RemoteException;
	
	public boolean validateVideo(String video) throws RemoteException;
	
	public void finalizeVideo(String video) throws RemoteException;
	
	public void finalizeVideoSubserver(String video) throws RemoteException;
}
