package shared.interfaces;

import shared.ClientData;
import shared.Data;
import shared.Room;

import javax.security.auth.login.LoginException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface SubserverInterface extends Remote {
	void assignUser(ClientData client) throws RemoteException;
	
	void uplaodVideoDataToCentral(String video, Data data, String owner) throws RemoteException, LoginException;
	
	void uploadCentralToSubserver(String video, Data data) throws RemoteException;
	
	Room getRoomData(int room) throws RemoteException;
	
	void setRoomData(int room, long time, boolean paused) throws RemoteException;
	
	void createRoom(Room room) throws RemoteException;
	
	long status() throws RemoteException;
	
	ArrayList<String> getAllVideoNames() throws RemoteException;
	
	ArrayList<Room> getRooms(String username) throws RemoteException;
	
	ArrayList<String> getUsers() throws RemoteException;
	
	void requestVideoFromSubserver(String video, String username) throws RemoteException, LoginException;
	
	void clearData() throws RemoteException;
	
	boolean videoNotExist(String video, String owner) throws RemoteException;
	
	void finalizeVideo(String video, String owner) throws RemoteException, LoginException;
	
	void finalizeVideoFromCentral(String video) throws RemoteException;
	
	void setId(int id, long wakeupTime) throws RemoteException;
}
