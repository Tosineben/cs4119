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

        System.out.println("Client starting with port " + clientPort); //TODO: remove

        ClientHelper.Init(serverIP, serverPort);
        ClientHelper helperInstance = ClientHelper.Instance();

        // listen for user input on one thread
        new Thread(new UserListener(helperInstance)).start();

        // listen for messages from server on another thread
        new Thread(new ServerListener(helperInstance, clientPort)).start();

    } //main

}
