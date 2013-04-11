import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class ServerHelper {

    // singleton
    private static ServerHelper singleton;
    public static void Init(DatagramSocket senderSocket) {
        singleton = new ServerHelper(senderSocket);
    }
    public static ServerHelper Instance() {
        return singleton;
    }

    private UnreliableUDP unreliableUDP; // no packets dropped from server to client
    private ServerPacketContentCreator packetContentCreator;
    private HashMap<String, String> pendingGameRequestsBySender;
    private HashMap<String, ClientModel> clients;
    private HashMap<String, GameBoard> games;
    private HashMap<String, Integer> clientCountByIP;
    private DatagramSocket senderSocket;

    private ServerHelper(DatagramSocket senderSocket) {
        this.unreliableUDP = new UnreliableUDP();
        this.packetContentCreator = new ServerPacketContentCreator();
        this.pendingGameRequestsBySender = new HashMap<String, String>();
        this.clients = new HashMap<String, ClientModel>();
        this.games = new HashMap<String, GameBoard>();
        this.clientCountByIP = new HashMap<String, Integer>();
        this.senderSocket = senderSocket;
    }

    public void Login(String clientName, int clientPort, String clientIP) throws IOException {
        boolean isValidLogin = false;

        if (!IsLoggedIn(clientName)) {

            int numClientsAtIP = clientCountByIP.containsKey(clientIP) ? clientCountByIP.get(clientIP) : 0;

            if (numClientsAtIP < 5) {
                ClientModel model = new ClientModel(clientName, clientIP, clientPort);
                clients.put(clientName, model);
                clientCountByIP.put(clientIP, numClientsAtIP + 1);
                isValidLogin = true;
            }
        }

        String message = packetContentCreator.AckLogin(isValidLogin);
        SendToClient(clientPort, message);
    }

    public void Logout(String clientName) throws IOException {
        // remove pending game requests from this client
        String otherClient = pendingGameRequestsBySender.remove(clientName);
        if (otherClient != null) {
            clients.get(otherClient).CurentState = ClientModel.State.Free;
        }

        // update pending game requests to this client to 'failed'
        for (Map.Entry<String, String> kvp : pendingGameRequestsBySender.entrySet()) {
            if (kvp.getValue().equals(clientName)) {
                String clientThatRequestedGame = kvp.getKey();
                clients.get(clientThatRequestedGame).CurentState = ClientModel.State.Free;
                String message = packetContentCreator.AckChoose(clientName, "F");
                SendToClient(clientThatRequestedGame, message);
            }
        }

        // forfeit any active game
        if (games.containsKey(clientName)) {
            String opponentName = games.get(clientName).GetOtherPlayerName(clientName);
            String winnerMsg = packetContentCreator.GameResult("W");
            SendToClient(opponentName, winnerMsg);
            GameOver(clientName, opponentName);
        }

        // reduce client ip count
        ClientModel client = clients.get(clientName);
        if (client != null) {
            clientCountByIP.put(client.IP, clientCountByIP.get(client.IP) - 1);
        }

        // remove the client
        clients.remove(clientName);
    }

    public void ListClients(String clientName) throws IOException {
        // list ALL clients, not "other" clients because this is how "Test Cases.docx" works
        ArrayList<ClientModel> allClients = new ArrayList<ClientModel>(clients.values());

        // send them in alphabetical order
        Collections.sort(allClients, new Comparator<ClientModel>() {
            @Override
            public int compare(ClientModel o1, ClientModel o2) {
                return o1.Name.compareTo(o2.Name);
            }
        });

        String message = packetContentCreator.ListClients(allClients    );
        SendToClient(clientName, message);
    }

    public void ChoosePlayer(String clientName, String otherClientName) throws IOException {
        boolean isClientFree = IsLoggedIn(clientName) && clients.get(clientName).CurentState == ClientModel.State.Free;
        boolean isOtherClientFree = IsLoggedIn(otherClientName) && clients.get(otherClientName).CurentState == ClientModel.State.Free;

        // make sure both clients are free and that client isn't choosing them self
        if (!isClientFree || !isOtherClientFree || clientName.equals(otherClientName)) {
            String message = packetContentCreator.AckChoose(otherClientName, "F");
            SendToClient(clientName, message);
            return;
        }

        // both clients are now in 'decision' state
        clients.get(clientName).CurentState = ClientModel.State.Decision;
        clients.get(otherClientName).CurentState = ClientModel.State.Decision;

        // send game request to other player
        String message = packetContentCreator.GameRequest(clientName);
        SendToClient(otherClientName, message);

        // add pending game request so we can validate AckChoose
        pendingGameRequestsBySender.put(clientName, otherClientName);
    }

    public void AckChoose(String clientName, String otherClientName, String status) throws IOException {
        // make sure there is a pending game request
        if (!IsPendingGameRequest(otherClientName, clientName)) {
            // just do nothing, client is acknowledging a game request that doesn't exist
            return;
        }

        // remove pending game request
        pendingGameRequestsBySender.remove(otherClientName);

        // update state of both clients to busy or free
        if ("A".equals(status)) {
            clients.get(clientName).CurentState = ClientModel.State.Busy;
            clients.get(otherClientName).CurentState = ClientModel.State.Busy;

            GameBoard newGame = new GameBoard(clientName, otherClientName);
            games.put(clientName, newGame);
            games.put(otherClientName, newGame);

            // tell client that just accepted to move
            String playMsg = packetContentCreator.GameState(newGame.toString());
            SendToClient(clientName, playMsg);
        }
        else {
            clients.get(clientName).CurentState = ClientModel.State.Free;
            clients.get(otherClientName).CurentState = ClientModel.State.Free;
        }

        // inform original client of request status
        String ackChooseMsg = packetContentCreator.AckChoose(clientName, status);
        SendToClient(otherClientName, ackChooseMsg);
    }

    private void GameOver(String client1, String client2) {
        games.remove(client1);
        games.remove(client2);
        if (IsLoggedIn(client1)) {
            clients.get(client1).CurentState = ClientModel.State.Free;
        }
        if (IsLoggedIn(client2)) {
            clients.get(client2).CurentState = ClientModel.State.Free;
        }
    }

    public void Play(String clientName, int move) throws IOException {
        // make sure client is playing a game
        if (!games.containsKey(clientName)) {
            String message = packetContentCreator.AckPlay(MoveOutcome.OutOfTurn);
            SendToClient(clientName, message);
            return;
        }

        GameBoard board = games.get(clientName);
        MoveOutcome outcome = board.PlayMove(clientName, move);

        // make sure move was valid
        if (outcome != MoveOutcome.Ok) {
            String message = packetContentCreator.AckPlay(outcome);
            SendToClient(clientName, message);
            return;
        }

        String opponentName = board.GetOtherPlayerName(clientName);

        // check for draw
        if (board.IsDraw()) {
            String message = packetContentCreator.GameResult("D");
            SendToClient(clientName, message);
            SendToClient(opponentName, message);
            GameOver(clientName, opponentName);
            return;
        }

        // check for game over
        String winner = board.CheckForWinner();
        if (winner != null) {
            String winnerMsg = packetContentCreator.GameResult("W");
            String loserMsg = packetContentCreator.GameResult("L");
            if (winner.equals(clientName)) {
                SendToClient(clientName, winnerMsg);
                SendToClient(opponentName, loserMsg);
            }
            else {
                SendToClient(clientName, loserMsg);
                SendToClient(opponentName, winnerMsg);
            }
            GameOver(clientName, opponentName);
            return;
        }

        // tell other player about new board, now their turn to play
        String message = packetContentCreator.GameState(board.toString());
        SendToClient(opponentName, message);
    }

    private boolean IsPendingGameRequest(String fromClient, String toClient) {
        return pendingGameRequestsBySender.containsKey(fromClient)
                && pendingGameRequestsBySender.get(fromClient).equals(toClient);
    }

    private boolean IsLoggedIn(String clientName) {
        return clients.containsKey(clientName);
    }

    private void SendToClient(int clientPort, String message) throws IOException {
        // client ip is hard-coded as server's ip --> https://piazza.com/class#spring2013/csee4119/69
        String clientIP = InetAddress.getLocalHost().getHostAddress();
        unreliableUDP.Send(senderSocket, clientIP, clientPort, message);
    }

    private void SendToClient(String clientName, String message) throws IOException {
        int clientPort = clients.get(clientName).Port;
        SendToClient(clientPort, message);
    }

}
