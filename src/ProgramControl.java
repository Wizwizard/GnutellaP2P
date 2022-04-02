import java.io.*;

public class ProgramControl {
    public static Runtime runtime = Runtime.getRuntime();


    public static String consumeInputStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "GBK"));
        String s;
        StringBuilder sb = new StringBuilder();
        while ((s = br.readLine()) != null) {
            System.out.println(s);

            sb.append(s);
        }

        return sb.toString();
    }

//    public static void initSuperServers() {
//        for(int i = 0; i < 10; i ++) {
//            int superPeerId = 20000 + 1 + i;
//            int port = superPeerId + 10000;
//            String neibors = Constant.SUPER_PEER_NEIGHBORS_CONFIG[i];
//            String logPath = Constant.BASE_DIR + "SuperPeers\\SuperPeer-" + superPeerId + ".log";
//
//            String command = "java -jar " + System.getProperty("user.dir") + "\\out\\artifacts\\SuperPeer_jar\\GnutellaP2P.jar " +
//                    superPeerId + " " + port + " " + neibors ;
//
//            execCommand(command, logPath);
//        }
//    }

    public static void initSuperServers() {
        for(int i = 0; i < 10; i ++) {
            int superPeerId = 20000 + 1 + i;
            int port = superPeerId + 10000;
            String neibors = Constant.SUPER_PEER_NEIGHBORS_CONFIG[i];
            String logPath = Constant.BASE_DIR + "SuperPeers\\SuperPeer-" + superPeerId + ".log";

            System.out.println("SuperPeer-" + superPeerId + " port: " + port + " init service, neibors: " + neibors);

            new Thread(() -> {
                try {
                    (new SuperPeer(superPeerId, port, neibors)).init_service();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();


        }
    }

    public static void initLeafNodes() {
        for(int i = 0; i < 8; i ++) {
            int superPeerPort = i + 30000 + 1 ;
            int leafId = 10000 + 2 * i + 1;
            String logPath = Constant.BASE_DIR + "LeafNodes\\LeafNode-" + leafId + ".log";

            System.out.println("LeafNode-" + leafId + " port: " + (leafId + 1) + " start, superPeerPort: " + superPeerPort);


            new Thread(() -> {
                try {
                    (new LeafNode(leafId, leafId+1, superPeerPort, Constant.CURRENT_MODE)).leafNodeStart("passivity");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        }
    }

//    public static void initLeafNodes() {
//        for(int i = 0; i < 9; i ++) {
//            int superPeerPort = i + 30000 + 1 ;
//            int leafId = 10000 + 2 * i + 1;
//            String logPath = Constant.BASE_DIR + "LeafNodes\\LeafNode-" + leafId + ".log";
//
//            String command = "java -jar " + System.getProperty("user.dir") + "\\out\\artifacts\\LeafNode_jar\\GnutellaP2P.jar " +
//                    superPeerPort;
//
//            execCommand(command, logPath);
//
//        }
//    }

    public static void execCommand(String command, String outputPath) {
        try {
            new Thread(() -> {
                try {
//                        String command = "java -jar " + System.getProperty("user.dir") + "\\src\\main\\resource\\p2p.jar 20001 20002 1-3-5";
                    System.out.println(command);
                    Process process = runtime.exec(command);
                    File file = new File(outputPath);
                    if(!file.exists()) {
                        file.createNewFile();
                    }
                    FileWriter fileWriter = new FileWriter(file.getAbsolutePath(), true);
                    BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                    consumeInputStream(process.getInputStream());
                    consumeInputStream(process.getErrorStream());
                    int proc = process.waitFor();

                    bufferedWriter.close();

//                    if (proc == 0) {
//                        System.out.println("Success!");
//                        System.out.println(inStr);
//                    } else {
//                        System.out.println("Failed!");
//                        System.out.println(errStr);
//                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public static void main(String args[]) {
        initSuperServers();
        initLeafNodes();
    }



}
