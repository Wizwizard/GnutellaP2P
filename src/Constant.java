import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Scanner;

/*
Store the public constant
 */

public class Constant {
    // Server Config
    public static final String SERVER_PORT = "1900";
    public static final String SERVER_ADDRESS = "127.0.0.1";
    public static final String CENTRAL_RMI_ADDRESS = "rmi://localhost:";
    public static final String SERVER_RMI_ADDRESS = "rmi://localhost:";
    public static final String PEER_RMI_ADDRESS = "rmi://localhost:";

    public static final String[] SUPER_PEER_NEIGHBORS_CONFIG = new String[]{
            "2-3-4", "1-4-5", "1-5-6", "1-6-7", "2-3-7",
            "3-4-8", "4-5-9", "6-9-10", "7-8-10", "8-9"
    };

    public static final int PUSH_MODE = 1;
    public static final int PULL_MODE = 2;
    public static final int CURRENT_MODE = PULL_MODE;
    public static final boolean openRandomUpdate = true;
    public static final String TESTING_FILE = "j";
    public static final int ACTIVE_LEAF = 2;

    /*
    1 debug
    2 testing
    3 normal
     */
    public static final int RUN_LV = 2;

    // ms
    public static final int TTR = 5000;

    // Base
    public static final String BASE_DIR = (new File("")).getAbsolutePath() + "\\static\\";
    public static final int MAX_DISTANCE = 99999999;
    public static final String PEER_NAME = "Leaf";
    public static final String SERVER_NAME = "SuperPeer";
    public static final String OWN_DIR = "own";
    public static final String DOWNLOAD_DIR = "download";

    // Thread
    public static final int PEER_MIN_THREAD = 2;
    public static final int PEER_MAX_THREAD = 10;

    // NetCode
    public static final String DOWNLOAD_REQUEST = "Download";
    public static final String REQEUST_SUCCESS = "Success";
    public static final String FILE_NOT_FOUND = "FileNotFound";

    //
    public static final int TTL = 10;

    public static Scanner scanner = new Scanner(System.in);

    public static void log(String str, String logPath) {
        File file = new File(logPath);
        FileWriter fileWriter = null;
        BufferedWriter bufferedWriter = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }

            fileWriter = new FileWriter(file.getAbsolutePath(), true);
            bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(str);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bufferedWriter != null) {
                try{
                    bufferedWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
