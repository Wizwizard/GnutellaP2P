import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.AtomicInteger;

public class LeafNode extends UnicastRemoteObject implements LeafNodeService {
    int leafId;
    int leafPort;
    int superPeerPort;
    String basePath;
    SuperPeerService superPeerService = null;
    int sequenceNumber = 0;

    AtomicInteger currentDownloadingNumber = new AtomicInteger(0);

    String logPath;

    LeafNode(int leafId, int leafPort, int superPeerPort) throws RemoteException {
        super();
        this.leafId = leafId;
        this.leafPort = leafPort;
        this.superPeerPort = superPeerPort;
        this.basePath = Constant.BASE_DIR + Constant.PEER_NAME + this.leafId + "\\";
        this.logPath = Constant.BASE_DIR + "LeafNodes\\" + "LeafNode-" + this.leafId + ".log";
    }

    @Override
    public int queryHit(String messageID, int TTL, String fileName, String leafNodeIP, int port_number) throws RemoteException {
        LeafNodeService leafNodeService = null;
        DataOutputStream fos = null;
        String filePath = basePath + fileName;

        try {
            leafNodeService = (LeafNodeService) Naming.lookup(
                    Constant.SERVER_RMI_ADDRESS + port_number + "/service"
            );
            byte[] content = leafNodeService.obtain(fileName);
            fos = new DataOutputStream(new FileOutputStream((new File(filePath))));
            fos.write(content);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
    public byte[] obtain(String fileName) throws RemoteException {
        // illegal check
        if (fileName == null || fileName.equals("")) {
            throw new RemoteException("Illegal fileName!");
        }

        String filePath = this.basePath + fileName;
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
            return content;
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

    private int handle_command(String inputString) {
//        System.out.println("LeafId-" + this.leafId + " receive command " + inputString);
        String command = inputString.split("-")[0];
        String fileName = inputString.split("-")[1];

        switch (command) {
            case "download":
                try {
                    this.superPeerService.query(
                            String.valueOf(this.leafId) + this.sequenceNumber,
                             String.valueOf(this.leafId), Constant.TTL, fileName);
                    this.sequenceNumber ++ ;
                } catch (Exception e) {
                    e.printStackTrace();
                    return -1;
                }
                break;
            default:
                // invalid command
                return -2;
        }
        return 0;
    }

    private void registerAllFile() throws RemoteException {
        String dirPath = this.basePath;
        File dir = new File(dirPath);
        File[] fileList = dir.listFiles();
        for ( File file : fileList) {
            if(file.isFile()) {
                this.superPeerService.registry(this.leafId, file.getName());
            }
        }
    }

    private void leafNodeRun(String mode) {
        while(true) {
            try {
                this.superPeerService = (SuperPeerService) Naming.lookup(
                        Constant.SERVER_RMI_ADDRESS + this.superPeerPort + "/service"
                );
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

        if(mode.equals("passivity")) {
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


    void leafNodeStart(String mode) {
        initService();
        leafNodeRun(mode);
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
                LeafNode leafNode = new LeafNode(leafId, port, superPeerPort);
                leafNode.leafNodeStart("initiative");
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
