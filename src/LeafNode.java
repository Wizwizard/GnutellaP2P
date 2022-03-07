import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

public class LeafNode extends UnicastRemoteObject implements LeafNodeService {
    int leafId;
    int leafPort;
    int superPeerPort;
    String basePath;
    SuperPeerService superPeerService = null;
    int sequenceNumber = 0;

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
                    Constant.SERVER_ADDRESS + port_number + "/service"
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
        String command = inputString.split(" ")[0];
        String fileName = inputString.split(" ")[1];

        switch (command) {
            case "download":
                try {
                    this.superPeerService.query(
                            String.valueOf(this.leafId) + this.sequenceNumber,
                             String.valueOf(this.leafId), Constant.TTL, fileName);
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

    private void leafNodeRun() {
        try{
            this.superPeerService = (SuperPeerService) Naming.lookup(
                    Constant.SERVER_ADDRESS + this.superPeerPort + "/service"
            );
        } catch (Exception e) {
            e.printStackTrace();
            return ;
        }

        String input_string;
        int returnCode;

        while(true) {
            System.out.println("Please input your command:");
            input_string = Constant.scanner.next();
            returnCode = handle_command(input_string);
            if (returnCode == 1) {
                break;
            } else if (returnCode == -1) {

            }
        }

    }


    private void leafNodeStart() {
        initService();

        leafNodeRun();
    }

    private void initService() {
        int port = this.leafPort;
        try {
            LeafNodeService leafNodeService = this;
            LocateRegistry.createRegistry(port);
            Naming.rebind(Constant.SERVER_ADDRESS + port + "/service", leafNodeService);
            System.out.println("Leaf Node service init, port: " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) {
        if(args.length < 1) {
            System.out.println("Missing parameter SuperPeerPort");
        }

        int superPeerPort = Integer.parseInt(args[0]);

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
            } catch (UnknownHostException | ConnectException e) {
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
                LeafNode leafNode = new LeafNode(leafId, port, superPeerPort);
                leafNode.leafNodeStart();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
