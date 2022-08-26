package shared.interfaces;

import shared.Data;
import shared.Room;

import javax.security.auth.login.LoginException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientInterface extends Remote {
	void uploadSubserverToClient(String video, Data data) throws RemoteException;
	
	void finalizeVideo(String video) throws RemoteException;
	
	void addUser(String user) throws RemoteException;
	
	void addRoom(Room room) throws RemoteException;
	
	void assignSubserver(SubserverInterface subserver, String username, long wakeupTime) throws RemoteException, LoginException;
}
