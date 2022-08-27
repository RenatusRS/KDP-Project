package shared.interfaces;

import shared.Data;
import shared.Room;
import shared.remote.ClientData;

import javax.security.auth.login.LoginException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface SubserverInterface extends Remote {
	void uploadVideoDataToCentral(String video, Data data, String owner) throws RemoteException, LoginException;
	
	void uploadCentralToSubserver(String video, Data data) throws RemoteException;
	
	Room getRoomData(int room) throws RemoteException, LoginException;
	
	void setRoomData(int room, long time, boolean paused) throws RemoteException, LoginException;
	
	void createRoom(Room room) throws RemoteException, LoginException;
	
	long status() throws RemoteException;
	
	ArrayList<String> getAllVideoNames() throws RemoteException;
	
	ArrayList<Room> getRooms(String username) throws RemoteException, LoginException;
	
	ArrayList<String> getUsers() throws RemoteException, LoginException;
	
	void requestVideoFromSubserver(String video, ClientData client) throws RemoteException, LoginException;
	
	boolean reserveVideo(String video, String owner) throws RemoteException, LoginException;
	
	void finalizeVideo(String video, String owner) throws RemoteException, LoginException;
	
	void finalizeVideoFromCentral(String video) throws RemoteException;
	
	void setId(int id, long wakeupTime) throws RemoteException;
}
