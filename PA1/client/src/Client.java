import java.io.IOException;
import java.net.ServerSocket;

public class Client {

    public static void main(String[] args) throws IOException {

        //TODO: remove this debug hack
        if (args.length == 0) {
            args = new String[3];
            ServerSocket server = new ServerSocket(0);
            int port = server.getLocalPort();
            server.close();
            args[0] = String.valueOf(port);
            args[1] = "127.0.0.1";
            args[2] = "4119";
        }

        int clientPort;
        String serverIP;
        int serverPort;

        try {
            clientPort = Integer.parseInt(args[0]);
            serverIP = args[1];
            serverPort = Integer.parseInt(args[2]);
        }
        catch (Exception e) {
            System.out.println("Invalid arguments: java Client <client_port> <server_ip> <server_port>");
            return;
        }

        ClientHelper.Init(serverIP, serverPort, clientPort);
        ClientHelper helperInstance = ClientHelper.Instance();

        new Thread(new ServerListener(helperInstance)).start(); // listen for messages from server
        new Thread(new UserListener(helperInstance)).start(); // listen for input from user
    }
}
