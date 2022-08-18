package shared.interfaces;

import shared.Room;
import shared.Video;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientInterface extends Remote {
	public void download(Video video) throws RemoteException;
	
	public void finalizeVideo(String video) throws RemoteException;
	
	public void addUser(String user) throws RemoteException;
	
	public void addRoom(Room room) throws RemoteException;
	
	public void assignSubserver(SubserverInterface subserver) throws RemoteException;
}
