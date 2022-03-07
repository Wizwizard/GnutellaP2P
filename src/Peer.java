import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/*
Peer
extends UnicastRemoteObject for remote call
implement retrieve func in PeerService

Main functions:
retrieve: return byte[] of target file to client
handle_command: handle the command input by user
 */
public class Peer extends UnicastRemoteObject implements PeerService{
    int peerId;
    static Scanner scanner = new Scanner(System.in);
    // thread pool
    ExecutorService es;
    String baseDir;
    int downloadTimes = 0;

    // define ReturnCode
    enum ReturnCode {
        SUCCESS, COMMAND_NONEXISTED, EXIT, EXCEPTION,
        NO_RESOURCE, CONNECT_EXCEPTION
    }

    @Override
    public byte[] retrieve(String fileName) throws RemoteException {

        // illegal check
        if(fileName == null || fileName.equals("")) {
            throw new RemoteException("Illegal fileName!");
        }

        String filePath = this.baseDir + fileName;
        File file = new File(filePath);
        if(!file.exists()) {
            throw new RemoteException("Remote Server peer-" + this.peerId + " doesn't has file " + fileName);
        }

        // read file to byte[]
        byte[] content = new byte[(int) file.length()];
        BufferedInputStream bis = null;
        try{
            bis = new BufferedInputStream(new FileInputStream(file));
            bis.read(content);
            downloadTimes ++;
            return content;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if(bis != null) {
                try {
                    bis.close();
                    bis = null;
                } catch (Exception e) {

                }
            }
        }
    }

    protected Peer(int peerId) throws RemoteException{
        this.peerId = peerId;
        this.baseDir = Constant.BASE_DIR + "Peer" + this.peerId + "\\";
        init();
    }

    private void init(){
        es = new ThreadPoolExecutor(Constant.PEER_MIN_THREAD, Constant.PEER_MAX_THREAD,
                60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

        // create dir for Peer
        File file = new File(this.baseDir);
        if(!file.exists()) {
            file.mkdir();
        }
    }

    // implement multi thread download server by socket
    @Deprecated
    private void server_run() {
        int peerServerPort = peerId + 1;
        ServerSocket serverSocket;

        try{
            // How to close properly?
            serverSocket = new ServerSocket(peerServerPort);
            System.out.println("Peer-" + this.peerId + " server start successfully!");
            Socket socket = null;

            while (true) {
                try {
                    socket = serverSocket.accept();
                    System.out.println("Address:" + socket.getRemoteSocketAddress() + " Connected");
                    Socket finalSocket = socket;
                    es.submit(new PeerServer(socket, this.baseDir));

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // init service
    private void service_init() {
        int port = this.peerId + 1;
        try {
            PeerService peerService = this;
            LocateRegistry.createRegistry(port);
            Naming.rebind(Constant.CENTRAL_RMI_ADDRESS + port + "/service", peerService);
            System.out.println("Download service init, port: " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     connect to CentralService
     Keep scan user input unless receive "exit"
     */
    protected void client_run() {
        ReturnCode returnCode;
        String input_string;

        try {
            CentralService centralService = (CentralService) Naming.lookup(
                    Constant.CENTRAL_RMI_ADDRESS + Constant.SERVER_PORT + "/service");
            System.out.println("Peer-" + this.peerId + " client start successfully!");
            while(true) {
                System.out.println("Please input your command:");
                input_string = scanner.next();
                returnCode = handle_command(input_string, centralService);
                if(returnCode == ReturnCode.EXIT) {
                    break;
                } else if (returnCode == ReturnCode.CONNECT_EXCEPTION) {

                    centralService = (CentralService) Naming.lookup(
                            Constant.CENTRAL_RMI_ADDRESS + Constant.SERVER_PORT + "/service");
                    System.out.println("Connect refresh.");
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    protected void peer_run() {
        // init rmi service
        service_init();
        // init client to receive user input
        client_run();
    }

    /*
    call centralService.search to get all serverIds
     */
    private List<Integer> search(CentralService centralService, String fileName) throws RemoteException{
        List<Integer> serverList = centralService.search(fileName);
        if(serverList != null && !serverList.isEmpty()) {
            // show server list
            System.out.println("Here are the server list:");
            for(int i = 0; i < serverList.size(); i ++)
            {
                System.out.println(serverList.get(i));
            }

            return serverList;
        } else {
            return null;
        }
    }

    // get nearestServer
    // can be implemented to get the serverId by bandwidth or other parameters
    private int searchNearestServer(CentralService centralService, String fileName) throws RemoteException {
        List<Integer> serverList = search(centralService, fileName);
        if (serverList == null) {
            // no resource for this file
            return -1;
        }

        int nearestServer = -1;
        int minDistance = Constant.MAX_DISTANCE;
        for (int serverId : serverList) {
            if (Math.abs(serverId - this.peerId) < minDistance) {
                minDistance = Math.abs(serverId - this.peerId);
                nearestServer = serverId;
            }
        }

        if(minDistance == 0) {
            // no need to download
            return 0;
        } else {
            System.out.println("Here are the nearest server: peer-" + nearestServer);
            return nearestServer;
        }
    }

    /*
    handle register, deregister, search, download
     */
    protected ReturnCode handle_command(String command, CentralService centralService) {
        String fileName;
        String input_string;
        int retCode;
        ReturnCode returnCode;

        try {
            System.out.println("Please input fileName:");
            fileName = scanner.next();
            switch (command) {
                case "register":
                    // check if local server has this file
                    if ((new File(this.baseDir+fileName)).exists()) {
                        retCode = centralService.registry(peerId, fileName);
                        if(retCode == 0)
                        {
                            System.out.println("file " + fileName + " has been registered!");
                        }
                    } else {
                        System.out.println("There is no " + fileName + "in local server, please check the input.");
                    }

                    break;

                case "deregister":

                    retCode = centralService.deregister(this.peerId, fileName);
                    if(retCode == 0)
                    {
                        System.out.println("file " + fileName + " has been deregistered!");
                    } else if (retCode == -1) {
                        System.out.println("file " + fileName + " has not been registered before!");
                    }
                    break;

                case "search":
                    System.out.println("DownloadTimes:" + downloadTimes);
                    search(centralService, fileName);
                    break;

                case "download":

                    int nearestServer = searchNearestServer(centralService, fileName);

                    // check return
                    if (nearestServer == -1) {
                        System.out.println("No resource for file " + fileName);
                    } else if (nearestServer == 0) {
                        System.out.println("Local server has already had this file!");
                    } else {
                        System.out.println("Please input a server to start downloading:");
                        String selectedServer = scanner.next();
//                        returnCode = download_file(nearestServer, fileName);

                        returnCode = retrieve_file(Integer.parseInt(selectedServer), fileName);
                        // handle download excepton
                        if(returnCode == ReturnCode.SUCCESS) {
                            // register if download successfully
                            centralService.registry(this.peerId, fileName);
                        } else {
                            System.out.println("Download failed, Please retry.");
                        }
                    }
                    break;
                case "exit":
                    return ReturnCode.EXIT;
                default:
                    return ReturnCode.COMMAND_NONEXISTED;
            }
        } catch (java.rmi.ConnectException e) {
            e.printStackTrace();
            return ReturnCode.CONNECT_EXCEPTION;
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnCode.EXCEPTION;
        }

        return ReturnCode.SUCCESS;
    }

    // download file by peerService
    private ReturnCode retrieve_file(int serverPeerId, String fileName) {
        int remoteServerPort = serverPeerId + 1;
        String filePath = this.baseDir + fileName;
        PeerService peerService = null;
        DataOutputStream fos = null;

        System.out.println("Peer-" + this.peerId + " try to download " + fileName + " from Peer-" + serverPeerId);

        try {
             peerService = (PeerService) Naming.lookup(
                    Constant.CENTRAL_RMI_ADDRESS + remoteServerPort + "/service");
            byte[] content = peerService.retrieve(fileName);
            fos = new DataOutputStream(new FileOutputStream(new File(filePath)));
            fos.write(content);

            System.out.println(filePath + " has been downloaded.");

        } catch (Exception e) {
            e.printStackTrace();
            return ReturnCode.EXCEPTION;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return ReturnCode.SUCCESS;
    }


    /*
    implement download file by socket
    has been replaced by RMI peerService
     */
    @Deprecated
    private ReturnCode download_file(int serverPeerId, String fileName) {
        int serverPeerPort = serverPeerId + 1;
        Socket socket;
        String filePath = this.baseDir + fileName;

        System.out.println("Peer-" + this.peerId + " try to download " + fileName + " from Peer-" + serverPeerId);

        try {
            socket = new Socket(Constant.SERVER_ADDRESS, serverPeerPort);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream fos = new DataOutputStream(new FileOutputStream(new File(filePath)));
            String response;

            dos.writeUTF(Constant.DOWNLOAD_REQUEST + " " + this.peerId + " " +fileName);
            dos.flush();
            response = dis.readUTF();
            //Ret fileLength
            String[] params = response.split(" ");
            if(params[0].equals(Constant.REQEUST_SUCCESS)) {
                int fileLength = Integer.parseInt(params[1]);
                byte[] buf = new byte[fileLength + 10];
                int len = 0;

                while ((len = dis.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                    fos.flush();
                }

                System.out.println(filePath + " has been downloaded.");

                fos.close();
                dis.close();
                dos.close();

                socket.close();

                return ReturnCode.SUCCESS;
            } else if (params[0] == Constant.FILE_NOT_FOUND) {
                System.out.println(fileName + "not found on server " + serverPeerId);
                return ReturnCode.EXCEPTION;
            } else {
                System.out.println("Response not correct!\nResponse:" + response);
                return ReturnCode.EXCEPTION;
            }

        } catch (IOException e)
        {
            e.printStackTrace();
            return ReturnCode.EXCEPTION;
        }
    }

    public static void main(String args[]) {
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
            int peerId = port - 1;
            try {
                Peer peer = new Peer(peerId);
                peer.peer_run();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
