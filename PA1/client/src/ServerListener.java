import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;

public class ServerListener implements Runnable {

    private UnreliableUDP unreliableUDP; // no packets dropped from server to client
    private DatagramSocket receiverSocket;
    private ClientHelper helper;

    public ServerListener(ClientHelper helper) throws IOException {
        this.unreliableUDP = new UnreliableUDP();
        this.helper = helper;
        this.receiverSocket = new DatagramSocket(helper.GetClientListeningPort());
    }

    @Override
    public void run() {

        System.out.println("ServerListener starting, port " + helper.GetClientListeningPort());

        while (true) {

            ReceivedMessage received;

            try {
                received = unreliableUDP.Receive(receiverSocket);
            }
            catch (IOException e) {
                System.out.println("Oops, there was a problem receiving data from the game server.");
                continue;
            }

            String[] msgParts = received.Message.split(",");
            String command = msgParts[0];

            if (command.equals("acklogin")) {
                HanldeAckLogin(msgParts);
            }
            else if (command.equals("ackls")) {
                HandleAckLs(msgParts);
            }
            else if (command.equals("request")) {
                HandleRequest(msgParts);
            }
            else if (command.equals("ackchoose")) {
                HandleAckChoose(msgParts);
            }
            else if (command.equals("play")) {
                HandlePlay(msgParts);
            }
            else if (command.equals("ackplay")) {
                HandleAckPlay(msgParts);
            }
            else if (command.equals("result")) {
                HandleResult(msgParts);
            }
            else {
                InvalidMessageFromServer();
            }
        }
    }

    // Handle methods validate message from server and display output appropriately

    private void HanldeAckLogin(String[] msgParts) {
        if (msgParts.length != 2) {
            InvalidMessageFromServer();
            return;
        }

        String state = msgParts[1];
        if (state.equals("F")) {
            System.out.println("login fail " + helper.Name);
            helper.IsLoggedIn = false;
        }
        else if (state.equals("S")) {
            System.out.println("login success " + helper.Name);
            helper.IsLoggedIn = true;
        }
        else {
            InvalidMessageFromServer();
        }
    }

    private void HandleAckLs(String[] msgParts) {
        if (msgParts.length%2 != 1) {
            InvalidMessageFromServer();
            return;
        }

        for (int i = 1; i < msgParts.length; i++) {
            if (i%2 == 1) {
                System.out.print(msgParts[i] + " ");
            }
            else {
                System.out.println(msgParts[i]);
            }
        }
        System.out.println("EOF");
    }

    private void HandleRequest(String[] msgParts) {
        if (msgParts.length != 2) {
            InvalidMessageFromServer();
            return;
        }

        String name = msgParts[1];
        System.out.println("request from " + name);
    }

    private void HandleAckChoose(String[] msgParts) {
        if (msgParts.length != 3) {
            InvalidMessageFromServer();
            return;
        }

        String name = msgParts[1];
        String status = msgParts[2];
        if (status.equals("A")){
            System.out.println("request accepted by " + name);
        }
        else if (status.equals("D")) {
            System.out.println("request denied by " + name);
        }
        else if (status.equals("F")) {
            System.out.println("request to " + name + " failed");
        }
        else {
            InvalidMessageFromServer();
        }
    }

    private void HandlePlay(String[] msgParts) {
        if (msgParts.length != 2) {
            InvalidMessageFromServer();
            return;
        }

        String gameState = msgParts[1];

        if (gameState.length() != 9) {
            InvalidMessageFromServer();
            return;
        }

        for (int i = 0; i < 9; i++) {
            char space = gameState.charAt(i);
            System.out.print(space == '0' ? '_' : space);
            System.out.print(' ');
            if ((i+1)%3 == 0) {
                System.out.println();
            }
        }
    }

    private void HandleAckPlay(String[] msgParts) {
        if (msgParts.length != 2) {
            InvalidMessageFromServer();
            return;
        }

        String status = msgParts[1];
        if (status.equals("O")) {
            System.out.println("Occupied");
        }
        else if (status.equals("T")) {
            System.out.println("Out of turn");
        }
        else {
            InvalidMessageFromServer();
        }
    }

    private void HandleResult(String[] msgParts) {
        if (msgParts.length != 2) {
            InvalidMessageFromServer();
            return;
        }

        String status = msgParts[1];
        if (status.equals("W")) {
            System.out.println(helper.Name + " win");
        }
        else if (status.equals("L")) {
            System.out.println(helper.Name + " lose");
        }
        else if (status.equals("D")) {
            System.out.println(helper.Name + " draw");
        }
        else {
            InvalidMessageFromServer();
        }
    }

    private void InvalidMessageFromServer() {
        System.out.println("Oops, received an invalid message from the game server.");
    }

}
