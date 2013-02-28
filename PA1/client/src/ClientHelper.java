import java.io.IOException;
import java.net.DatagramSocket;

public class ClientHelper {

    // singleton
    private static ClientHelper singleton;
    public static void Init(String serverIP, int serverPort, int clientPort) throws IOException {
        DatagramSocket clientSocket = new DatagramSocket(clientPort);
        singleton = new ClientHelper(serverIP, serverPort, clientSocket);
    }
    public static ClientHelper Instance() {
        return singleton;
    }

    private ReliableUDP reliableUDP;
    private ClientPacketContentCreator packetContentCreator;
    private String serverIP;
    private int serverPort;
    private DatagramSocket clientSocket;

    private ClientHelper(String serverIP, int serverPort, DatagramSocket clientSocket) {
        this.reliableUDP = new ReliableUDP();
        this.packetContentCreator = new ClientPacketContentCreator();
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.clientSocket = clientSocket;
    }

    // shared state
    public String ClientName;
    public boolean IsLoggedIn;
    public DatagramSocket GetClientSocket() { return clientSocket; }

    // one-time registration of the client at the server
    public void Login(String name) {
        if (IsLoggedIn) {
            AlreadyLoggedIn();
            return;
        }

        ClientName = name;
        String message = packetContentCreator.Login(name, clientSocket.getLocalPort());
        SendToServer(message);
    }

    // query for list of other clients on server
    public void QueryList() {
        if (!IsLoggedIn) {
            NotLoggedIn();
            return;
        }

        String message = packetContentCreator.QueryList(ClientName);
        SendToServer(message);
    }

    // initiate connection with another client to play game
    public void ChoosePlayer(String name2) {
        if (!IsLoggedIn) {
            NotLoggedIn();
            return;
        }

        String message = packetContentCreator.ChoosePlayer(ClientName, name2);
        SendToServer(message);
    }

    // accept game request from other client
    public void AcceptRequest(String name1) {
        if (!IsLoggedIn) {
            NotLoggedIn();
            return;
        }

        String message = packetContentCreator.AckRequest(ClientName, name1, true);
        SendToServer(message);
    }

    // deny game request from other client
    public void DenyRequest(String name1) {
        if (!IsLoggedIn) {
            NotLoggedIn();
            return;
        }

        String message = packetContentCreator.AckRequest(ClientName, name1, false);
        SendToServer(message);
    }

    // choose a cell to play
    public void PlayGame(int number) {
        if (!IsLoggedIn) {
            NotLoggedIn();
            return;
        }

        System.out.println(ClientName + " " + number);

        String message = packetContentCreator.PlayGame(ClientName, number);
        SendToServer(message);
    }

    // terminate connection with server
    public void Logout() {
        if (!IsLoggedIn) {
            NotLoggedIn();
            return;
        }

        System.out.println(ClientName + " logout");

        String message = packetContentCreator.Logout(ClientName);
        SendToServer(message);
    }

    private void SendToServer(String message) {
        reliableUDP.Send(clientSocket, serverIP, serverPort, message);
    }

    private void AlreadyLoggedIn() {
        System.out.println("Oops, already logged in as " + ClientName);
    }

    private void NotLoggedIn() {
        System.out.println("Oops, please login first.");
    }

}
