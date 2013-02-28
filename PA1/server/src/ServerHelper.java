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
    private DatagramSocket senderSocket;

    private ServerHelper(DatagramSocket senderSocket) {
        this.unreliableUDP = new UnreliableUDP();
        this.packetContentCreator = new ServerPacketContentCreator();
        this.pendingGameRequestsBySender = new HashMap<String, String>();
        this.clients = new HashMap<String, ClientModel>();
        this.games = new HashMap<String, GameBoard>();
        this.senderSocket = senderSocket;
    }

    public void Login(String clientName, int clientPort) throws IOException {
        boolean isValidName = !IsLoggedIn(clientName);

        if (isValidName) {
            ClientModel model = new ClientModel(clientName, clientPort);
            clients.put(clientName, model);
        }

        String message = packetContentCreator.AckLogin(isValidName);
        SendToClient(clientPort, message);
    }

    public void Logout(String clientName) throws IOException {
        // remove pending game requests from this client
        pendingGameRequestsBySender.remove(clientName);

        // update pending game requests to this client to 'failed'
        for (Map.Entry<String, String> kvp : pendingGameRequestsBySender.entrySet()) {
            if (kvp.getValue().equals(clientName)) {
                String clientThatRequestedGame = kvp.getKey();
                clients.get(clientThatRequestedGame).State = ClientState.Free;
                String message = packetContentCreator.AckChoose(clientName, RequestStatus.Failed);
                SendToClient(clientThatRequestedGame, message);
            }
        }

        // forfeit any active game
        if (games.containsKey(clientName)) {
            String opponentName = games.get(clientName).GetOtherPlayerName(clientName);
            String winnerMsg = packetContentCreator.GameResult(GameOutcome.Win);
            SendToClient(opponentName, winnerMsg);
            GameOver(clientName, opponentName);
        }

        // remove the client
        clients.remove(clientName);
    }

    public void ListOtherClients(String clientName) throws IOException {
        ArrayList<ClientModel> otherClients = new ArrayList<ClientModel>();
        for (ClientModel client : clients.values()) {
            if (!client.Name.equals(clientName)) {
                otherClients.add(client);
            }
        }

        // send them in alphabetical order
        Collections.sort(otherClients, new Comparator<ClientModel>() {
            @Override
            public int compare(ClientModel o1, ClientModel o2) {
                return o1.Name.compareTo(o2.Name);
            }
        });

        String message = packetContentCreator.ListClients(otherClients);
        SendToClient(clientName, message);
    }

    public void ChoosePlayer(String clientName, String otherClientName) throws IOException {
        // make sure client sending game request is free
        if (!IsLoggedIn(clientName) || clients.get(clientName).State != ClientState.Free) {
            String message = packetContentCreator.AckChoose(otherClientName, RequestStatus.Failed);
            SendToClient(clientName, message);
            return;
        }

        // make sure client receiving game request is free
        if (!IsLoggedIn(otherClientName) || clients.get(otherClientName).State != ClientState.Free) {
            String message = packetContentCreator.AckChoose(otherClientName, RequestStatus.Failed);
            SendToClient(clientName, message);
            return;
        }

        // both clients are now in 'decision' state
        clients.get(clientName).State = ClientState.Decision;
        clients.get(otherClientName).State = ClientState.Decision;

        // send game request to other player
        String message = packetContentCreator.GameRequest(clientName);
        SendToClient(otherClientName, message);

        // add pending game request so we can validate AckChoose
        pendingGameRequestsBySender.put(clientName, otherClientName);
    }

    public void AckChoose(String clientName, String otherClientName, RequestStatus status) throws IOException {
        // make sure there is a pending game request
        if (!IsPendingGameRequest(otherClientName, clientName)) {
            // just do nothing, client is acknowledging a game request that doesn't exist
            return;
        }

        // update state of both clients to busy or free
        if (status == RequestStatus.Accepted) {
            clients.get(clientName).State = ClientState.Busy;
            clients.get(otherClientName).State = ClientState.Busy;

            GameBoard newGame = new GameBoard(clientName, otherClientName);
            games.put(clientName, newGame);
            games.put(otherClientName, newGame);

            // tell client that just accepted to move
            String playMsg = packetContentCreator.GameState(newGame.toString());
            SendToClient(clientName, playMsg);
        }
        else {
            clients.get(clientName).State = ClientState.Free;
            clients.get(otherClientName).State = ClientState.Free;
        }

        // inform original client of request status
        String ackChooseMsg = packetContentCreator.AckChoose(clientName, status);
        SendToClient(otherClientName, ackChooseMsg);
    }

    private void GameOver(String client1, String client2) {
        games.remove(client1);
        games.remove(client2);
        if (IsLoggedIn(client1)) {
            clients.get(client1).State = ClientState.Free;
        }
        if (IsLoggedIn(client2)) {
            clients.get(client2).State = ClientState.Free;
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
            String message = packetContentCreator.GameResult(GameOutcome.Draw);
            SendToClient(clientName, message);
            SendToClient(opponentName, message);
            GameOver(clientName, opponentName);
            return;
        }

        // check for game over
        String winner = board.CheckForWinner();
        if (winner != null) {
            String winnerMsg = packetContentCreator.GameResult(GameOutcome.Win);
            String loserMsg = packetContentCreator.GameResult(GameOutcome.Loss);
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
