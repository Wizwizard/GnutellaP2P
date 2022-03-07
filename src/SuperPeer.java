import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SuperPeer extends UnicastRemoteObject implements SuperPeerService{
    List<Integer> neighborSuperPeerList;
    HashMap<String, String> messagePairMap;
    HashMap<String, List<Integer>> fileIndexMap;
    int superPeerId;
    int port;

    String logPath;

    SuperPeer(int superPeerId, int port, String neighborSuperPeers) throws RemoteException {
        super();
        this.superPeerId = superPeerId;
        this.port = port;
        neighborSuperPeerList = new ArrayList<>();
        for(String neighborSuperPeer: neighborSuperPeers.split("-")) {
            neighborSuperPeerList.add(Integer.parseInt(neighborSuperPeer));
        }

        this.logPath = Constant.BASE_DIR + "SuperPeers\\" + "SuperPeer-" + this.superPeerId + ".log";


        messagePairMap = new HashMap<>();
        fileIndexMap = new HashMap<>();

    }

    @Override
    public int registry(int peerId, String fileName) throws RemoteException {
        if(!fileIndexMap.containsKey(fileName))
            fileIndexMap.put(fileName, new ArrayList<>());
        if(!fileIndexMap.get(fileName).contains(peerId)) {
            fileIndexMap.get(fileName).add(peerId);
            System.out.println("Leaf-" + peerId + " registered file " + fileName);
        }

        // Success
        return 0;
    }



    /*
    0 normal
    1 Has seen this messageID, discard
    2 TTL = 0
    -1 exception
     */
    @Override
    public int query(String messageID, String upstreamID, int TTL, String fileName) throws RemoteException {
        if (messagePairMap.containsKey(messageID)) {

            // Has seen this messageID, discard
            System.out.println(this.superPeerId + " has seen the messageID:" + messageID + " so discard this message!");
            return 1;
        }

        messagePairMap.put(messageID, upstreamID);
        // TO-DO delete when oversize

        SuperPeerService superPeerService = null;

        if (fileIndexMap.containsKey(fileName)) {
            int leafID = fileIndexMap.get(fileName).get(0);
            try {
                superPeerService = (SuperPeerService) Naming.lookup(
                        Constant.SERVER_ADDRESS + (upstreamID + 1) + "/service");
            } catch (Exception e) {
                e.printStackTrace();
            }
            // port = id + 1
            if( superPeerService == null ) {
                // something error cause connect service failed
                return -1;
            }
            superPeerService.queryHit(messageID, Constant.TTL, fileName,
                    String.valueOf(leafID), leafID + 1);

            System.out.println(this.superPeerId + " queryHit " + fileName + " on "+ leafID + " and send back to upstreamPort:" + upstreamID);

            return 0;
        } else {
            TTL = TTL - 1;
            if( TTL == 0) {
                // TTL 0 stop forward
                System.out.println(messageID + " TTL == 0, Query forward Stop!");
                return 2;
            }
            for(int neighborPort : neighborSuperPeerList) {
                try {
                    superPeerService = (SuperPeerService) Naming.lookup(
                            Constant.SERVER_ADDRESS + (neighborPort) + "/service");


                    superPeerService.query(messageID, String.valueOf(this.superPeerId), TTL, fileName);

                    System.out.println(this.superPeerId + " broadcast query to port:" + neighborPort);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return 0;
        }
    }

    @Override
    public int queryHit(String messageID, int TTL, String fileName, String leafNodeIP, int port_number) throws RemoteException {
        SuperPeerService superPeerService = null;
        TTL = TTL - 1;
        if (TTL == 0) {
            // stop route
            System.out.println(messageID + " TTL == 0, QueryHit Stop!");
            return 2;
        }
        try {
            int upstreamPort = Integer.parseInt(messagePairMap.get(messageID)) + 1;
            superPeerService = (SuperPeerService) Naming.lookup(
                    Constant.SERVER_ADDRESS + upstreamPort + "/service"
            );
            superPeerService.queryHit(messageID, TTL - 1, fileName, leafNodeIP, port_number);
            System.out.println(this.superPeerId + " receive queryHit " + fileName + " on "+ leafNodeIP + " and send back to upstreamPort:" + upstreamPort);

        } catch (Exception e) {
            e.printStackTrace();
            // exception
            return -1;
        }
        return 0;
    }

    private void init_service() {
        try {
            LocateRegistry.createRegistry(this.port);
            Naming.rebind(Constant.SERVER_RMI_ADDRESS + this.port + "/service", this);
            System.out.println("SuperPeer-" + this.superPeerId + " Start!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) {
        if(args.length < 3) {
            System.out.println("SuperPeer parameters missing!");
            return ;
        }

        int superPeerId = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        String neiborSuperPeers = args[2];

//        System.out.println(superPeerId);
//        System.out.println(port);
//        System.out.println(neiborSuperPeers);

        try {
            SuperPeer superPeer = new SuperPeer(superPeerId, port, neiborSuperPeers);
            superPeer.init_service();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

//class MessagePair {
//    String messageId;
//    String upstreamPeerId;
//
//    MessagePair(String messageId, String upstreamPeerId) {
//        this.messageId = messageId;
//        this.upstreamPeerId = upstreamPeerId;
//    }
//}
