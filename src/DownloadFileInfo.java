import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class DownloadFileInfo implements Serializable {
    int originServerId;
    long TTR;
    long lastMdfdTime;
    int versionNumber;
    byte[] fileData;

    DownloadFileInfo(int originServerId, long TTR, long lastMdfdTime, int versionNumber, byte[] fileData) throws RemoteException {
        super();
        this.originServerId = originServerId;
        this.TTR = TTR;
        this.lastMdfdTime = lastMdfdTime;
        this.fileData = fileData;
        this.versionNumber = versionNumber;
    }
}