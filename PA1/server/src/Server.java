import java.io.IOException;
import java.net.DatagramSocket;

public class Server {

    private static ServerHelper helper;

    public static void main(String[] args) {
        try {
            // server hard-coded to listen at 4119
            DatagramSocket receiverSocket = new DatagramSocket(4119);

            // server needs reliable UDP listener
            ReliableUDP reliableUDP = new ReliableUDP();

            ServerHelper.Init(receiverSocket);
            helper = ServerHelper.Instance();

            // receive info from clients forever
            while (true) {

                ReceivedMessage received = reliableUDP.Receive(receiverSocket);
                String[] msgParts = received.Message.split(",");
                String command = msgParts[0];

                if (command.equals("login")) {
                    HandleLogin(msgParts, received.FromIP);
                }
                else if (command.equals("list")) {
                    HandleList(msgParts);
                }
                else if (command.equals("choose")) {
                    HandleChoose(msgParts);
                }
                else if (command.equals("ackchoose")) {
                    HandleAckChoose(msgParts);
                }
                else if (command.equals("play")) {
                    HandlePlay(msgParts);
                }
                else if (command.equals("logout")) {
                    HandleLogout(msgParts);
                }
                else {
                    InvalidMessageFromClient();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle methods validate input and then pass work off to ServerHelper

    private static void HandleLogin(String[] msgParts, String fromIP) throws IOException {
        if (msgParts.length != 4) {
            InvalidMessageFromClient();
            return;
        }

        String name = msgParts[2];
        Integer port = Utility.TryParseInt(msgParts[3]);

        if (port == null) {
            InvalidMessageFromClient();
        }
        else {
            helper.Login(name, port, fromIP);
        }
    }

    private static void HandleList(String[] msgParts) throws IOException {
        if (msgParts.length != 3) {
            InvalidMessageFromClient();
            return;
        }

        String name = msgParts[2];
        helper.ListClients(name);
    }

    private static void HandleChoose(String[] msgParts) throws IOException {
        if (msgParts.length != 4) {
            InvalidMessageFromClient();
            return;
        }

        String name = msgParts[2];
        String otherClientName = msgParts[3];
        helper.ChoosePlayer(name, otherClientName);
    }

    private static void HandleAckChoose(String[] msgParts) throws IOException {
        if (msgParts.length != 5) {
            InvalidMessageFromClient();
            return;
        }

        String name = msgParts[2];
        String otherClientName = msgParts[3];
        String statusString = msgParts[4];

        RequestStatus status;
        if (statusString.equals("A")) {
            status = RequestStatus.Accepted;
        }
        else if (statusString.equals("D")) {
            status = RequestStatus.Denied;
        }
        else {
            InvalidMessageFromClient();
            return;
        }

        helper.AckChoose(name, otherClientName, status);
    }

    private static void HandlePlay(String[] msgParts) throws IOException {
        if (msgParts.length != 4) {
            InvalidMessageFromClient();
            return;
        }

        String name = msgParts[2];
        Integer move = Utility.TryParseInt(msgParts[3]);

        if (move == null) {
            InvalidMessageFromClient();
        }
        else {
            helper.Play(name, move);
        }
    }

    private static void HandleLogout(String[] msgParts) throws IOException {
        if (msgParts.length != 3) {
            InvalidMessageFromClient();
            return;
        }

        String name = msgParts[2];
        helper.Logout(name);
    }

    private static void InvalidMessageFromClient() {
        System.out.println("Invalid message from client.");
    }

}
