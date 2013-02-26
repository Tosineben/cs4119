import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class ServerHelper {

    private ReliableUDP reliableUDP;
    private ServerPacketContentCreator packetContentCreator;

    public ServerHelper() {
        reliableUDP = new ReliableUDP();
        packetContentCreator = new ServerPacketContentCreator();
    }

    // list of clients in alphabetical order
    private HashMap<String, ClientModel> clients;

    public void Login(String clientName, int clientPort) {
        boolean isValidName = !IsLoggedIn(clientName);

        if (isValidName) {
            ClientModel model = new ClientModel(clientName, clientPort);
            clients.put(clientName, model);
        }

        String message = packetContentCreator.AckLogin(isValidName);
        reliableUDP.Send("127.0.0.1", clientPort, message);
    }

    private boolean IsLoggedIn(String clientName) {
        return clients.containsKey(clientName);
    }

    private void SendToClient(String clientName, String message) {
        int port = clients.get(clientName).Port;
        reliableUDP.Send("127.0.0.1", port, message);
    }

    public void ClientList(String clientName) {
        if (!IsLoggedIn(clientName)) {
            // throw
        }

        ArrayList<ClientModel> otherClients = new ArrayList<ClientModel>();
        for (ClientModel client : clients.values()) {
            if (!client.Name.equals(clientName)) {
                otherClients.add(client);
            }
        }

        String message = packetContentCreator.ClientList(otherClients);
        SendToClient(clientName, message);
    }

    // decide whether game is over and who won/lost
    public void IsGameOver() {

    }

    // decide whether client move is valid
    public void IsValidMove() {

    }

    // decide who's turn it is to play
    public void NextClientTurn() {

    }

    // if client sends request to play with another client, reject new
    // requests to both of those clients until someone accepts/denies

}
