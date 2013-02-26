import Enums.RequestStatus;

import java.io.IOException;
import java.net.DatagramSocket;

public class Server {

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


    }

    private static void HandleLogout(ServerHelper helper, String[] msgParts) throws IOException {
        if (msgParts.length != 3) {
            System.out.println("Invalid request.");
            return;
        }

        String name = msgParts[2];
        helper.Logout(name);
    }


    public static void main(String[] args) throws IOException {

        int receiverPort = 4119;
        int bufferSize = 1024;

        DatagramSocket receiverSocket = new DatagramSocket(receiverPort);
        System.out.println("Server starting");
        System.out.println("Receiving at port " + receiverPort + " ...");

        byte[] buffer = new byte[bufferSize];

        ServerHelper helper = new ServerHelper();
        ReliableUDP reliableUDP = new ReliableUDP();

        // receive info from clients forever
        while (true) {

            ReceivedMessage received = reliableUDP.Receive(receiverSocket, buffer);
            String msg = received.Message;
            String[] msgParts = msg.split(",");

            if (msg.startsWith("login")) {
                HandleLogin(helper, msgParts);
            }
            else if (msg.startsWith("list")) {
                HandleList(helper, msgParts);
            }
            else if (msg.startsWith("choose")) {
                HandleChoose(helper, msgParts);
            }
            else if (msg.startsWith("ackchoose")) {
                HandleAckChoose(helper, msgParts);
            }
            else if (msg.startsWith("play")) {
                HandlePlay(helper, msgParts);
            }
            else if (msg.startsWith("logout")) {
                HandleLogout(helper, msgParts);
            }
            else {
                System.out.println("Unrecognized client message.");
            }

        } //while

    } //main

}
