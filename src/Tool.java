import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Tool {
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
        for (int peerId = minPeerNumber; peerId <= maxPeerNumber; peerId += 2) {
            dirPath = Constant.BASE_DIR + Constant.PEER_NAME + peerId;
            if (!(new File(dirPath).exists())){
                (new File(dirPath)).mkdir();
            }
            for (i = 1; i <= 10; i ++) {
                fileName = "" + peerId + filePostfix[i-1];
                filePath = dirPath + "\\" + fileName;
                try {
                    createFile(filePath, i * 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void LeafNodeDownloadPerformanceTesting() throws RemoteException {
        LeafNode leafNode = new LeafNode(10061, 10062, 30010);
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


    public static void main(String args[]) {
        // generate files with different size for testing
//        batchGenerateFile();

        // batch testing of Central Server search service
//        try {
//            CentralServerSearchTesting();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        // batch testing of Peer retrieve service
        try {
            LeafNodeDownloadPerformanceTesting();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
