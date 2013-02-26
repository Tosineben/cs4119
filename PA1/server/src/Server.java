import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Calendar;

public class Server {

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

            ReliableUDP.ReceivedMessage receivedMsg = reliableUDP.Receive(receiverSocket, buffer);
            String msg = receivedMsg.Message;
            String[] msgParts = msg.split(",");

            if (msg.startsWith("login")) {
                if (msgParts.length != 4) {
                    System.out.print("Invalid request.");
                    continue;
                }

                String name = msgParts[2];
                int port;
                try {
                    port = Integer.parseInt(msgParts[3]);
                }
                catch (NumberFormatException e) {
                    System.out.print("Invalid request.");
                    continue;
                }

                helper.Login(name, port);
            }
            else if (msg.startsWith("list")) {
                if (msgParts.length != 3) {
                    System.out.println("Invalid request.");
                    continue;
                }

                String name = msgParts[2];
                helper.ClientList(name);
            }
            else if (msg.startsWith("choose")) {
                helper.Login(input);
            }
            else if (msg.startsWith("ackchoose")) {
                helper.ChoosePlayer(input);
            }
            else if (msg.startsWith("play")) {
                helper.AcceptRequest(input);
            }
            else if (msg.startsWith("logout")) {
                helper.DenyRequest(input);
            }
            else {
                System.out.println("Unrecognized client message.");
            }

        } //while

    } //main

}
