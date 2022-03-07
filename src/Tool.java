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

    public static void CentralServerSearchTesting() throws MalformedURLException, NotBoundException, RemoteException {
        CentralService centralService = (CentralService) Naming.lookup(
                Constant.CENTRAL_RMI_ADDRESS + Constant.SERVER_PORT + "/service");

        int[] lvs = {1000, 10000, 20000};
        long startTime;
        long endTime;
        double avgTime;
        int i;

        // generate the index firstly
        for (i = 0; i < 100; i ++) {
            centralService.registry(i, "fixedFile");
        }

        for (int j = 0; j < lvs.length; j ++) {
            ExecutorService executorService = Executors.newFixedThreadPool(lvs[j]);
            startTime = System.currentTimeMillis();
            for (i = 0; i < lvs[j]; i ++) {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            centralService.search("fixedFile");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            executorService.shutdown();
            endTime = System.currentTimeMillis();
            avgTime = (endTime - startTime) / (lvs[j] / lvs[0]);
            System.out.println("Request start at " + startTime +
                    " Request end at " + endTime +
                    " Request number:" + lvs[j] +
                    " avgTime: " + avgTime +"ms");
        }
    }

    public static void PeerRetrieveTesting() throws MalformedURLException, NotBoundException, RemoteException {
        PeerService peerService = (PeerService) Naming.lookup(
                Constant.CENTRAL_RMI_ADDRESS + 10002 + "/service"
        ) ;

        int[] lvs = {1000, 10000, 20000};
        long startTime;
        long endTime;
        double avgTime;
        int i;

        for (int j = 0; j < lvs.length; j ++) {
            ExecutorService executorService = Executors.newFixedThreadPool(lvs[j]);
            startTime = System.currentTimeMillis();
            for (i = 0; i < lvs[j]; i ++) {
                executorService.submit(() -> {
                    try {
                        // 6M size file
                        peerService.retrieve("test.jpg");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            executorService.shutdown();
            endTime = System.currentTimeMillis();
            avgTime = (endTime - startTime) / (lvs[j] / lvs[0]);
            System.out.println("Request start at " + startTime +
                    " Request end at " + endTime +
                    " Request number:" + lvs[j] +
                    " avgTime: " + avgTime +"ms");
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
            batchGenerateFile();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
