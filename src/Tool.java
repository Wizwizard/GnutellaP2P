import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Tool {

    static Random random = new Random();

    private static void createFile(final String filename, final long sizeInBytes) throws IOException {
        File file = new File(filename);
        file.createNewFile();

        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.setLength(sizeInBytes);
        raf.close();
    }

    public static void batchGenerateFile() {

        String[] filePostfix = "a b c d e f g h i j".split(" ");
        int maxPeerNumber = 10061;
        int minPeerNumber = 10001;
        int i;
        String fileName;
        String filePath;
        String dirPath;
        String ownPath;
        String downloadPath;
        for (int peerId = minPeerNumber; peerId <= maxPeerNumber; peerId += 2) {
            dirPath = Constant.BASE_DIR + Constant.PEER_NAME + peerId + "\\";
            if (!(new File(dirPath).exists())){
                (new File(dirPath)).mkdir();
            }

            // clear before create
            File peerRootDir = new File(dirPath);
            for (File f : peerRootDir.listFiles())
                f.delete();

            ownPath = dirPath + Constant.OWN_DIR;
            downloadPath = dirPath + Constant.DOWNLOAD_DIR;

            if (!(new File(ownPath)).exists()) {
                (new File(ownPath)).mkdir();
            }

            if (!(new File(downloadPath)).exists()) {
                (new File(downloadPath)).mkdir();
            }


            for (i = 1; i <= 10; i ++) {
                fileName = "" + peerId + filePostfix[i-1];
                filePath = ownPath + "\\" + fileName;
                try {
                    createFile(filePath, i * 10000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void LeafNodeDownloadPerformanceTesting() throws RemoteException {
        LeafNode leafNode = new LeafNode(10061, 10062, 30010, Constant.CURRENT_MODE);
        leafNode.leafNodeStart("passivity");
        String messageID = "10061";
        int sequenceNumber = 0;
        String filename = "j";
        System.out.println("Download start at " + System.currentTimeMillis());

        for(int i = 0; i < 200; i ++ ){
            leafNode.currentDownloadingNumber.incrementAndGet();
            leafNode.superPeerService.query(messageID + i, String.valueOf(leafNode.leafId), Constant.TTL,
                    (10000 + 1 + 2 * (i % 9)) + filename);
        }
    }


    public static void PushTesting(int downloadLeafNode) {
        int numTotalDownload = 100;

        for (int i = 10-downloadLeafNode; i < 10; i ++ ) {
            int superPeerPort = i + 30000 + 1 ;
            int leafId = 10000 + 2 * i + 1;
            new Thread(()->{
                try {
                    LeafNode leafNode = new LeafNode(leafId, leafId + 1, superPeerPort, Constant.CURRENT_MODE);
                    leafNode.leafNodeStart("active");
                    for(int j = 0; j < numTotalDownload; j ++) {
                        int k = random.nextInt(10-downloadLeafNode);
                        String downloadFile = (10000 + 2 * k + 1) + Constant.TESTING_FILE;
                        leafNode.handle_command("download-" + downloadFile);
                    }
                    System.out.println("Leaf-" + leafId + " expired rate: " + ((leafNode.numExpiredDownload * 100.0) / leafNode.numTotalDownload));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }).start();

        }
    }



    public static void main(String args[]) {
        PushTesting(Constant.ACTIVE_LEAF);
        // generate files with different size for testing
//        batchGenerateFile();
        // batch testing of Central Server search service
//        try {
//            CentralServerSearchTesting();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        // batch testing of Peer retrieve service
//        try {
//            LeafNodeDownloadPerformanceTesting();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

    }
}
