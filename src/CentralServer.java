import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CentralServer {
    public static void main(String[] args) {
        int serverPort = Integer.parseInt(Constant.SERVER_PORT);
        try {
            CentralServiceImpl centralService = new CentralServiceImpl();
            LocateRegistry.createRegistry(serverPort);
            Naming.rebind(Constant.CENTRAL_RMI_ADDRESS + serverPort + "/service", centralService);
            System.out.println("Start server, port: " + serverPort);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
