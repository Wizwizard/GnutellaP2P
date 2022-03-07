import java.rmi.Remote;
import java.rmi.RemoteException;

/*
PeerService
Define a interface for peer client to download file
 */
public interface PeerService extends Remote {
    byte[] retrieve(String fileName) throws RemoteException;
}
