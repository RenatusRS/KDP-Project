package shared.interfaces;

import shared.ClientData;
import shared.Data;
import shared.Room;
import shared.SubserverData;

import javax.security.auth.login.LoginException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface CentralServerInterface extends Remote {
	long register(ClientData client) throws RemoteException, LoginException;
	
	long login(ClientData client) throws RemoteException, LoginException;
	
	void addSubserver(SubserverData server) throws RemoteException, NotBoundException;
	
	void uploadVideoDataToCentral(String video, Data data, String owner) throws RemoteException, LoginException;
	
	Room getRoomData(int room) throws RemoteException;
	
	void setRoomData(int room, long time, boolean paused) throws RemoteException;
	
	void createRoom(Room room) throws RemoteException;
	
	ArrayList<Room> getRooms(String username) throws RemoteException;
	
	ArrayList<String> getUsers() throws RemoteException;
	
	ArrayList<String> getAllVideoNames() throws RemoteException;
	
	void requestVideoFromCentral(String video, int subserver) throws RemoteException, LoginException;
	
	boolean videoNotExist(String video, String owner) throws RemoteException;
	
	void finalizeVideoOnCentral(String video, String owner) throws RemoteException, LoginException;
}
