package shared.interfaces;

import shared.Room;
import shared.SubserverData;
import shared.User;
import shared.Video;

import javax.security.auth.login.LoginException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface CentralServerInterface extends Remote {
	public void register(User user) throws RemoteException, LoginException;
	
	public void login(User user) throws RemoteException, LoginException;
	
	public SubserverData addSubserver(SubserverData server) throws RemoteException, NotBoundException;
	
	public void addVideo(Video video) throws RemoteException;
	
	public Room getRoom(int room) throws RemoteException;
	
	public void syncRoom(int room, long time, boolean paused) throws RemoteException;
	
	public void createRoom(Room room) throws RemoteException;
	
	public ArrayList<Room> getRooms(String username) throws RemoteException;
	
	public ArrayList<String> getUsers() throws RemoteException;
	
	public ArrayList<String> getAllVideoNames() throws RemoteException;
	
	public void getVideo(String video, int subserver) throws RemoteException, LoginException;
	
	public boolean validateName(String video) throws RemoteException;
	
	public void finalizeVideo(String video) throws RemoteException;
}
