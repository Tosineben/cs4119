import Enums.RequestStatus;

import java.io.IOException;
import java.net.DatagramSocket;

public class Server {

    public static void main(String[] args) throws IOException {
        // server hard-coded to listen at 4119
        int receiverPort = 4119;
        DatagramSocket receiverSocket = new DatagramSocket(receiverPort);

        System.out.println("Server starting, receiving at port " + receiverPort); //TODO: remove

        byte[] buffer = new byte[1024];

        ServerHelper helperInstance = ServerHelper.Instance();
        ReliableUDP reliableUDP = new ReliableUDP(); // need reliable listener

        // receive info from clients forever
        while (true) {

            ReceivedMessage received = reliableUDP.Receive(receiverSocket, buffer);
            String[] msgParts = received.Message.split(",");
            String command = msgParts[0];

            if (command.equals("login")) {
                HandleLogin(helperInstance, msgParts);
            }
            else if (command.equals("list")) {
                HandleList(helperInstance, msgParts);
            }
            else if (command.equals("choose")) {
                HandleChoose(helperInstance, msgParts);
            }
            else if (command.equals("ackchoose")) {
                HandleAckChoose(helperInstance, msgParts);
            }
            else if (command.equals("play")) {
                HandlePlay(helperInstance, msgParts);
            }
            else if (command.equals("logout")) {
                HandleLogout(helperInstance, msgParts);
            }
            else {
                System.out.println("Unrecognized client message.");
            }
        }
    }

    // Handle command methods validate input and then pass work off to ServerHelper

    private static void HandleLogin(ServerHelper helper, String[] msgParts) throws IOException {
        if (msgParts.length != 4) {
            System.out.print("Invalid request.");
            return;
        }

        String name = msgParts[2];
        int port;
        try {
            port = Integer.parseInt(msgParts[3]);
        }
        catch (NumberFormatException e) {
            System.out.print("Invalid request.");
            return;
        }

        helper.Login(name, port);
    }

    private static void HandleList(ServerHelper helper, String[] msgParts) throws IOException {
        if (msgParts.length != 3) {
            System.out.println("Invalid request.");
            return;
        }

        String name = msgParts[2];
        helper.ListOtherClients(name);
    }

    private static void HandleChoose(ServerHelper helper, String[] msgParts) throws IOException {
        if (msgParts.length != 4) {
            System.out.println("Invalid request.");
            return;
        }

        String name = msgParts[2];
        String otherClientName = msgParts[3];
        helper.ChoosePlayer(name, otherClientName);
    }

    private static void HandleAckChoose(ServerHelper helper, String[] msgParts) throws IOException {
        if (msgParts.length != 5) {
            System.out.println("Invalid request.");
            return;
        }

        String name = msgParts[2];
        String otherClientName = msgParts[3];
        String statusString = msgParts[4];

        // status must be either 'A' for accepted or 'D' for denied
        if (!statusString.equals("A") && !statusString.equals("D")) {
            System.out.println("Invalid request.");
            return;
        }

        RequestStatus status = statusString.equals("A") ? RequestStatus.Accepted : RequestStatus.Denied;

        helper.AckChoose(name, otherClientName, status);
    }

    private static void HandlePlay(ServerHelper helper, String[] msgParts) throws IOException {
        if (msgParts.length != 4) {
            System.out.println("Invalid request.");
            return;
        }

        String name = msgParts[2];
        int move;
        try {
            move = Integer.parseInt(msgParts[3]);
        }
        catch (NumberFormatException e) {
            System.out.println("Invalid request.");
            return;
        }

        helper.Play(name, move);
    }

    private static void HandleLogout(ServerHelper helper, String[] msgParts) throws IOException {
        if (msgParts.length != 3) {
            System.out.println("Invalid request.");
            return;
        }

        String name = msgParts[2];
        helper.Logout(name);
    }

}
