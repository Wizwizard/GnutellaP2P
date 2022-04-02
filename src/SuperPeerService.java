import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SuperPeerService extends Remote {
    int registry(int peerId, String fileName) throws RemoteException;
    int query(String messageID, String upstreamID, int TTL, String fileName) throws RemoteException;
    int queryHit(String messageID, int TTL, String fileName, String leafNodeIP, int port_number) throws RemoteException;
    int invalidation(String msgId, int serverId, String filename, int versionNumber) throws RemoteException;
}
