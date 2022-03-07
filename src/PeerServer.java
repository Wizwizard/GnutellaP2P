import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;

/*
Implement a PeerServer for multi thread
This class is not used in the latest project version
We use PeerService to implement retrieve file by RMI
 */
public class PeerServer implements Runnable, PeerService{
    Socket socket;
    String baseDir;

    PeerServer(Socket socket, String baseDir) {
        this.socket = socket;
        this.baseDir = baseDir;
    }

    @Override
    public void run() {
        handle_download(this.socket);
    }

    private void handle_download(Socket socket){
        String filePath;

        // Handle Exception
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            String request_string = in.readUTF();
            // req peerId fileName
            String[] params = request_string.split(" ");

            if(params[0].equals(Constant.DOWNLOAD_REQUEST)) {
                filePath = this.baseDir + params[2];
                File file = new File(filePath);
                int f_length = (int) file.length();

                System.out.println("Peer-" + params[1] + " try to download " + filePath);

                dos.writeUTF(Constant.REQEUST_SUCCESS + " " + f_length);
                dos.flush();

                DataInputStream fis = new DataInputStream(new FileInputStream(filePath));
                byte[] buf = new byte[f_length + 10];
                int len = 0;

                while ((len = fis.read(buf)) != -1) {
                    dos.write(buf, 0, len);
                }

                dos.flush();
                System.out.println(filePath + " has been sent to peer-" + params[1]);

                in.close();
                dos.close();
                fis.close();
            } else {
                System.out.println("Peer-" + params[1] + " Wrong request: " + params[0]);
            }
            socket.close();
        } catch (EOFException e) {
//            System.out.println("Remote socket closed.");
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] retrieve(String fileName) throws RemoteException {
        return new byte[0];
    }
}
