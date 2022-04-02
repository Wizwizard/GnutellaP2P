import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class LeafNode extends UnicastRemoteObject implements LeafNodeService {
    int leafId;
    int leafPort;
    int superPeerPort;
    String basePath;
    String ownPath;
    String downloadPath;
    SuperPeerService superPeerService = null;
    int sequenceNumber = 0;

    HashMap<String, FileInfo> fileMap = null;
    List<String> msglist = null;

    AtomicInteger currentDownloadingNumber = new AtomicInteger(0);

    String logPath;
    int consistencyMode;

    public int numTotalDownload = 0;
    public int numExpiredDownload = 0;

    LeafNode(int leafId, int leafPort, int superPeerPort, int consistencyMode) throws RemoteException {
        super();
        this.leafId = leafId;
        this.leafPort = leafPort;
        this.superPeerPort = superPeerPort;
        this.basePath = Constant.BASE_DIR + Constant.PEER_NAME + this.leafId + "\\";
        this.ownPath = this.basePath + Constant.OWN_DIR + "\\";
        this.downloadPath = this.basePath + Constant.DOWNLOAD_DIR + "\\";
        this.logPath = Constant.BASE_DIR + "LeafNodes\\" + "LeafNode-" + this.leafId + ".log";
        this.fileMap = new HashMap<>();
        this.consistencyMode = consistencyMode;

        this.sequenceNumber = (new Random()).nextInt(1000);
        this.msglist = new ArrayList<>();
    }

    DownloadFileInfo downloadFile(int port_number, String fileName) {
        LeafNodeService leafNodeService = null;
        DataOutputStream fos = null;
        String filePath = downloadPath + fileName;
        DownloadFileInfo downloadFileInfo = null;

        try {
            System.out.println("Leaf-" + this.leafId + " try to download file " + fileName + " from port-" + port_number);
            leafNodeService = (LeafNodeService) Naming.lookup(
                    Constant.SERVER_RMI_ADDRESS + port_number + "/service"
            );
            // how to handle the TTL or version number?
            downloadFileInfo = leafNodeService.obtain(fileName);
            fos = new DataOutputStream(new FileOutputStream((new File(filePath))));
            fos.write(downloadFileInfo.fileData);

            this.superPeerService.registry(this.leafId, fileName);
            if (fileMap.containsKey(fileName)) {
                FileInfo fileInfo = fileMap.get(fileName);
                fileInfo.TTR = downloadFileInfo.TTR;
                fileInfo.versionNumber = downloadFileInfo.versionNumber;
                fileInfo.lastMdfdTime = downloadFileInfo.lastMdfdTime;
                fileInfo.isValid = true;
            } else {
                    fileMap.put(fileName, new FileInfo(downloadFileInfo.versionNumber, downloadFileInfo.originServerId,
                            true, filePath, downloadFileInfo.TTR, downloadFileInfo.lastMdfdTime));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return downloadFileInfo;
    }

    int updateFile(String fileName) {
        FileInfo fileInfo = fileMap.get(fileName);
        if(!fileInfo.isValid) {
            if(downloadFile(fileInfo.originServerId+1, fileName) == null) {
                // error
                return -1;
            }
        }
        return  0;
    }

    @Override
    public int queryHit(String messageID, int TTL, String fileName, String leafNodeIP, int port_number) throws RemoteException {

        if(msglist.contains(messageID)) {
            // discard
            return 0;
        }

        msglist.add(messageID);

        DownloadFileInfo downloadFileInfo = downloadFile(port_number, fileName);
        if (downloadFileInfo == null) {
            return -1;
        }

        // testing
        if (Constant.RUN_LV == 2) {
            int originPort = downloadFileInfo.originServerId + 1;
            try {
                LeafNodeService leafNodeService = (LeafNodeService) Naming.lookup(
                        Constant.SERVER_RMI_ADDRESS + originPort + "/service"
                );
                long ret = leafNodeService.poll(fileName, downloadFileInfo.versionNumber);
                if (ret == -2) {
                    numExpiredDownload ++;
                }
                numTotalDownload ++;

            } catch (NotBoundException | MalformedURLException e) {
                e.printStackTrace();
            }
        }


        System.out.println("File " + fileName + " on LeafNode-" + leafNodeIP + " has been downloaded in " + this.leafId + " successfully!");
        int currentNumber = this.currentDownloadingNumber.decrementAndGet();
        System.out.println("Current download number:" + currentNumber);
        if(currentNumber == 0) {
            System.out.println("All Download finished at " + System.currentTimeMillis());
        }
        return 0;
    }

    @Override
    public DownloadFileInfo obtain(String fileName) throws RemoteException {
        // illegal check
        if (fileName == null || fileName.equals("")) {
            throw new RemoteException("Illegal fileName!");
        }

        FileInfo fileInfo = fileMap.get(fileName);
        String filePath = fileInfo.filePath;
        DownloadFileInfo downloadFileInfo = null;


        // Push mode
        // only check valid flag
        if (fileInfo.originServerId != this.leafId) {
            if (this.consistencyMode == Constant.PUSH_MODE) {
                if (!fileInfo.isValid) {

                    downloadFileInfo = downloadFile(fileInfo.originServerId + 1, fileName);
                    if(downloadFileInfo == null) {
                        System.out.println("update file failed");
                        return null;
                    }
//                fileInfo.isValid = true;
//                fileInfo.versionNumber = downloadFileInfo.versionNumber;
//                fileInfo.lastMdfdTime = downloadFileInfo.lastMdfdTime;
//                fileInfo.TTR = downloadFileInfo.TTR;
                }
            } else {
                // pull mode
                // check if TTR expired
                long currentTime = System.currentTimeMillis();

                if (!fileInfo.isValid || currentTime > (fileInfo.TTR + fileInfo.lastMdfdTime)) {
                    downloadFileInfo = downloadFile(fileInfo.originServerId + 1, fileName);
//                fileInfo.isValid = true;
//                fileInfo.lastMdfdTime = downloadFileInfo.lastMdfdTime;
//                fileInfo.TTR = downloadFileInfo.TTR;
//                fileInfo.versionNumber = downloadFileInfo.versionNumber;
                }
            }
        }


        File file = new File(filePath);
        if (!file.exists()) {
            throw new RemoteException("Remote Server leaf-" + this.leafId + " doesn't has file " + fileName);
        }

        // read file to byte[]
        byte[] content = new byte[(int) file.length()];
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            bis.read(content);
//            downloadTimes ++;
            long currentTime = System.currentTimeMillis();
            long TTR = fileInfo.TTR;
            // PULL mode && own this file
            // refresh the TTR
            if (consistencyMode == Constant.PULL_MODE && fileInfo.originServerId == this.leafId) {
                TTR = currentTime + Constant.TTR - fileInfo.lastMdfdTime;
            }
            return (new DownloadFileInfo(fileInfo.originServerId, TTR, fileInfo.lastMdfdTime, fileInfo.versionNumber, content));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                    bis = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public int invalidation(String msgId, int serverId, String filename, int versionNumber) throws RemoteException {
        /*
        1. mark status to invalid
        2. then register the index
        3. lazy mode: only download when need to use
         */
        System.out.println("Leaf-" + this.leafId + " receive invalidation file:" + filename +
                " orginalServerId:" + serverId + " versionNumber:" + versionNumber);

        if(!fileMap.containsKey(filename)) {
            // no this file?
            System.out.println(this.leafId + " receive invalidation but don't have this file " + filename);
            return -2;
        }

        FileInfo fileInfo = fileMap.get(filename);
        if (fileInfo.versionNumber != versionNumber) {
            fileInfo.isValid = false;
            fileInfo.versionNumber = versionNumber;
            fileInfo.originServerId = serverId;
            System.out.println("leaf-" + this.leafId + " receive file " +
                    filename + " with versionNumber " + versionNumber + " invalidation and set invalid");
        }

        // re-registry
        // to-do check success
        superPeerService.registry(this.leafId, filename);

        return 0;
    }

    @Override
    public long poll(String filename, int versionNumber) throws RemoteException {
        FileInfo fileInfo = fileMap.get(filename);
        if (fileInfo == null) {
            System.out.println("file " + filename + "not exist on server " + this.leafId);
            // no such file
            return -1;
        }

        if (fileInfo.versionNumber == versionNumber) {
            long newTTR = System.currentTimeMillis() + Constant.TTR - fileInfo.lastMdfdTime;
            return newTTR;
        } else {
            // invalid
            return -2;
        }
    }

    int handle_command(String inputString) {
        System.out.println("LeafId-" + this.leafId + " receive command " + inputString);
        String command = inputString.split("-")[0];
        String fileName = inputString.split("-")[1];

        switch (command) {
            case "download":
                try {
                    this.superPeerService.query(
                            String.valueOf(this.leafId) + this.sequenceNumber,
                             String.valueOf(this.leafId), Constant.TTL, fileName);
                    System.out.println("LeafId-" + this.leafId + " query for file " + fileName);
                    this.sequenceNumber ++ ;
                } catch (Exception e) {
                    e.printStackTrace();
                    return -1;
                }
                break;
            case "update":
                // update version
                update_version(fileName);
                break;
            case "refresh":
                int ret = updateFile(fileName);
                if(ret == -1) {
                    System.out.println(this.leafId + " encountered issue when update file " + fileName);
                } else {
                    System.out.println(this.leafId + " updated file " + fileName + " successfully!");
                }
                break;
            default:
                // invalid command
                return -2;
        }
        return 0;
    }

    private void registerAllFile() throws RemoteException {
        String dirPath = this.ownPath;
        File dir = new File(dirPath);
        File[] fileList = dir.listFiles();
        FileInfo fileInfo = null;
        for ( File file : fileList) {
            if(file.isFile()) {
                // init
                int versionNumber = 0;
                int originalServerId = this.leafId;
                boolean isValid = true;
                String filePath = file.getPath();
                fileInfo = new FileInfo(versionNumber, originalServerId, isValid, filePath);
                // name can't be same
                fileMap.put(file.getName(), fileInfo);

                this.superPeerService.registry(this.leafId, file.getName());
                System.out.println("Leaf-" + this.leafId + " registered file " + file.getName() + " on superPeerPort-" + this.superPeerPort);
            }
        }

    }

    void update_version(String filename) {
        FileInfo fileInfo = fileMap.get(filename);
        fileInfo.lastMdfdTime = System.currentTimeMillis();
        fileInfo.versionNumber += 1;

        String msgId = this.leafId + "" + filename + "" + fileInfo.versionNumber;

        // only push mode to broadcast invalidation
        if (consistencyMode == Constant.PUSH_MODE) {
            try {
                superPeerService.invalidation(msgId, this.leafId, filename, fileInfo.versionNumber);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    private void leafNodeRun(String mode) {
        while(true) {
            try {
                this.superPeerService = (SuperPeerService) Naming.lookup(
                        Constant.SERVER_RMI_ADDRESS + this.superPeerPort + "/service"
                );
                System.out.println("Leaf-" + this.leafId + " successfully connected to superPeerPort-" + this.superPeerPort);
                break;
            } catch (NotBoundException e) {
                System.out.println("Seems that SuperPeer-" + this.superPeerPort + " has not inited! Retrying...");
            }
            catch (Exception e) {
                System.out.println("LeafNode-" + this.leafId + " exception !");
                e.printStackTrace();
                return;
            }
        }

        try {
            registerAllFile();
        } catch (RemoteException e) {
            e.printStackTrace();
            return ;
        }

        if(!mode.equals("initiative") ) {
            return;
        } else {
            String input_string;
            int returnCode;

            while (true) {
                System.out.println("Please input your command:");
                input_string = Constant.scanner.next();
                returnCode = handle_command(input_string);
                if (returnCode == 1) {
                    break;
                } else if (returnCode == -1) {

                }
            }
        }

    }

    void checkExpirPeriodically() {
        new Thread(()->{
            while(true) {
                try {
                    Thread.sleep(Constant.TTR);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

//                System.out.println("Leaf-" + this.leafId + " start to check expired file");

                for(String filename: fileMap.keySet()) {
                    FileInfo fileInfo = fileMap.get(filename);
                    // only check downloaded file
                    LeafNodeService leafNodeService;
                    if (fileInfo.originServerId != this.leafId) {
                        long currentTime = System.currentTimeMillis();
                        // check expired
                        if (currentTime > (fileInfo.lastMdfdTime + fileInfo.TTR)) {
                            fileInfo.isValid = false;
                            System.out.println("Leaf-" + this.leafId + " found file " + filename + " expired due to TTR!");
                        }

                        // check if version updated
                        try {
                            leafNodeService = (LeafNodeService) Naming.lookup(
                                    Constant.SERVER_RMI_ADDRESS + (fileInfo.originServerId + 1) + "/service"
                            );
                            long TTR = leafNodeService.poll(filename, fileInfo.versionNumber);
                            if(TTR == -1) {
                                //error retry?
                            } else if (TTR == -2) {
                                //expired
                                fileInfo.isValid = false;
                                System.out.println("Leaf-" + this.leafId + " found file " + filename + " expired due to version updated!");
                            } else {
                                // new TTR
                                fileInfo.TTR = TTR;
                            }
                        } catch (NotBoundException | MalformedURLException | RemoteException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        }).start();
    }

    void randomUpdateRoutine() {
        Random random = new Random();
        String filename = this.leafId + "j";
        new Thread(()->{
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            update_version(filename);
        }).start();
    }


    void leafNodeStart(String mode) {
        initService();
        if(this.consistencyMode == Constant.PULL_MODE) {
            checkExpirPeriodically();
        }
        leafNodeRun(mode);
        if(mode.equals("passivity") && Constant.openRandomUpdate) {
            randomUpdateRoutine();
        }
    }

    private void initService() {
        int port = this.leafPort;
        try {
            LeafNodeService leafNodeService = this;
            LocateRegistry.createRegistry(port);
            Naming.rebind(Constant.SERVER_RMI_ADDRESS + port + "/service", leafNodeService);
            System.out.println("Leaf Node service init, port: " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) {
//        if(args.length < 1) {
//            System.out.println("Missing parameter SuperPeerPort");
//        }
//
//        int superPeerPort = Integer.parseInt(args[0]);

        // init id & port
        String host = "localhost";
        Socket socket;
        int port;

        // Check if the port in use
        // hardcode max number of port to 20000
        for(port = 10002; port < 20000; port+=2) {
            try {
                socket = new Socket(host, port);
                socket.close();
            } catch (UnknownHostException | java.net.ConnectException e) {
                // port unused
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(port > 19999) {
            System.out.println("No available ports!\nPeer terminated.");
        } else {
            // define peerId = port - 1 for convenient
            int leafId = port - 1;
            try {
                int superPeerPort = (leafId-10000+1)/2 + 30000;
                LeafNode leafNode = new LeafNode(leafId, port, superPeerPort, Constant.CURRENT_MODE);
                leafNode.leafNodeStart("initiative");
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}

class FileInfo {
    int versionNumber;
    int originServerId;
    boolean isValid;
    String filePath;
    long TTR;
    long lastMdfdTime;

    FileInfo(int versionNumber, int originServerId, boolean isValid, String filePath) {
        this(versionNumber, originServerId, isValid, filePath, 0, 0);
    }

    FileInfo(int versionNumber, int originServerId, boolean isValid, String filePath, long TTR, long lastMdfdTime) {
        this.versionNumber = versionNumber;
        this.originServerId = originServerId;
        this.isValid = isValid;
        this.filePath = filePath;
        this.TTR = TTR;
        this.lastMdfdTime = lastMdfdTime;
    }
}


