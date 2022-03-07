import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface CentralService extends Remote {

    int registry(int peerId, String fileName) throws RemoteException;
    List<Integer> search(String fileName) throws RemoteException;
    int deregister(int peerId, String fileName) throws RemoteException;
}
