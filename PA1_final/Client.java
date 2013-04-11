
public class Client {

    public static void main(String[] args) {
        try {
            int clientPort = Integer.parseInt(args[0]);
            String serverIP = args[1];
            int serverPort = Integer.parseInt(args[2]);

            ClientHelper.Init(serverIP, serverPort, clientPort);
            ClientHelper helperInstance = ClientHelper.Instance();

            new Thread(new ServerListener(helperInstance)).start(); // listen for messages from server
            new Thread(new UserListener(helperInstance)).start(); // listen for input from user
        }
        catch (Exception e) {
            System.out.println("Usage: java Client <client_port> <server_ip> <server_port>");
        }
    }
}
