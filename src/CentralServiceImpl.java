import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CentralServiceImpl extends UnicastRemoteObject implements CentralService {
    HashMap<String, ArrayList<Integer>> fileIndexes;

    protected CentralServiceImpl() throws RemoteException {
        fileIndexes = new HashMap<>();
    }

    /*
    return: code
    peerId = peer port
     */
    @Override
    public int registry(int peerId, String fileName) throws RemoteException {
        if(!fileIndexes.containsKey(fileName))
            fileIndexes.put(fileName, new ArrayList<>());
        if(!fileIndexes.get(fileName).contains(peerId)) {
            fileIndexes.get(fileName).add(peerId);
            System.out.println("Peer-" + peerId + " registered file " + fileName);
        }

        // Success
        return 0;
    }

    @Override
    public List<Integer> search(String fileName) throws RemoteException {
        return fileIndexes.get(fileName);
    }

    @Override
    public int deregister(int peerId, String fileName) throws RemoteException {
        if(fileIndexes.containsKey(fileName) && fileIndexes.get(fileName).contains(peerId))
        {
            for(int i = 0; i < fileIndexes.get(fileName).size(); i ++)
            {
                if(fileIndexes.get(fileName).get(i) == peerId) {
                    fileIndexes.get(fileName).remove(i);
                    break;
                }
            }
            System.out.println("Peer-" + peerId + " deregistered file " + fileName);
            // success
            return 0;
        } else {
            // error
            return -1;
        }
    }
}
