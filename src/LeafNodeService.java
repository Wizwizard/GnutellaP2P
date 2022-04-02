import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LeafNodeService extends Remote {
    public int queryHit(String messageID, int TTL, String fileName, String leafNodeIP, int port_number) throws RemoteException;
    public DownloadFileInfo obtain(String fileName) throws RemoteException;
    public int invalidation(String msgId, int serverId, String filename, int versionNumber) throws RemoteException;
    // return TTR
    public long poll(String filename, int versionNumber) throws RemoteException;
}
