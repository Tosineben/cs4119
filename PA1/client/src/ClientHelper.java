import java.net.DatagramSocket;

public class ClientHelper {

    // singleton
    private static ClientHelper singleton;
    public static void Init(String serverIP, int serverPort) {
        singleton = new ClientHelper(serverIP, serverPort);
    }
    public static ClientHelper Instance() {
        return singleton;
    }

    private ReliableUDP reliableUDP;
    private ClientPacketContentCreator packetContentCreator;
    private String serverIP;
    private int serverPort;

    private ClientHelper(String serverIP, int serverPort) {
        this.reliableUDP = new ReliableUDP();
        this.packetContentCreator = new ClientPacketContentCreator();
        this.serverIP = serverIP;
        this.serverPort = serverPort;
    }

    // shared state
    public String Name;
    public boolean IsLoggedIn;

    // one-time registration of the client at the server
    public void Login(String name) {
        if (IsLoggedIn) {
            return;
        }

        Name = name;
        String message = packetContentCreator.Login(name);
        SendToServer(message);
    }

    // query for list of other clients on server
    public void QueryList() {
        if (!IsLoggedIn) {
            return;
        }

        String message = packetContentCreator.QueryList(Name);
        SendToServer(message);
    }

    // initiate connection with another client to play game
    public void ChoosePlayer(String name2) {
        if (!IsLoggedIn) {
            return;
        }

        String message = packetContentCreator.ChoosePlayer(Name, name2);
        SendToServer(message);
    }

    // accept game request from other client
    public void AcceptRequest(String name1) {
        if (!IsLoggedIn) {
            return;
        }

        String message = packetContentCreator.AckRequest(Name, name1, true);
        SendToServer(message);
    }

    // deny game request from other client
    public void DenyRequest(String name1) {
        if (!IsLoggedIn) {
            return;
        }

        String message = packetContentCreator.AckRequest(Name, name1, false);
        SendToServer(message);
    }

    // choose a cell to play
    public void PlayGame(int number) {
        if (!IsLoggedIn) {
            return;
        }

        System.out.println(Name + " " + number);

        String message = packetContentCreator.PlayGame(Name, number);
        SendToServer(message);
    }

    // terminate connection with server
    public void Logout() {
        if (!IsLoggedIn) {
            return;
        }

        System.out.println(Name + " logout");

        String message = packetContentCreator.Logout(Name);
        SendToServer(message);
    }

    private void SendToServer(String message) {
        reliableUDP.Send(serverIP, serverPort, message);
    }

}
