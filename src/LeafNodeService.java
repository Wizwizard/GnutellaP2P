import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LeafNodeService extends Remote {
    int queryHit(String messageID, int TTL, String fileName, String leafNodeIP, int port_number) throws RemoteException;
    byte[] obtain(String fileName) throws RemoteException;
}
