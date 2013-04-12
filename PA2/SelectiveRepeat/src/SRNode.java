import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Calendar;

public class SRNode {

    public static void main(String[] args) {

        // TODO remove debug
        if (args.length == 0){
            args = new String[5];
            args[0] = "11111";
            args[1] = "22222";
            args[2] = "10";
            args[3] = "300";
            args[4] = "0.56";
        }

        int sourcePort;     // send msgs from this port
        int destPort;       // receive msgs from this port
        int windowSize;     // length of the ACK window
        int timeoutMs;      // packet timeout
        double lossRate;    // packet loss rate

        try {
            sourcePort = Integer.parseInt(args[0]);
            destPort = Integer.parseInt(args[1]);
            windowSize = Integer.parseInt(args[2]);
            timeoutMs = Integer.parseInt(args[3]);
            lossRate = Double.parseDouble(args[4]);
        }
        catch (Exception e) {
            System.err.println("Usage: SRNode <source-port> <destination-port> <window-size> <time-out> <loss-rate>");
            return;
        }

        try {
            mainImpl(sourcePort, destPort, windowSize, timeoutMs, lossRate);
        }
        catch (Exception e) {
            System.err.println("Fiery death.");
            System.err.println(e);
        }
    }

    private static void mainImpl(int sendPort, int rcvPort, int windowSize, int timeoutMs, double lossRate) throws SocketException {

        DatagramSocket senderSocket = new DatagramSocket(sendPort);
        DatagramSocket receiverSocket = new DatagramSocket(rcvPort);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        // accept user input forever
        while (true) {

            String message = GetMessageFromUser();

            if (message == null) {
                InvalidUserInput();
                continue;
            }

            SendMessage(message);
        }

    }

    private static String GetMessageFromUser() {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String userInput;

        // read from std in
        try {
            userInput = br.readLine();
        } catch (IOException e) {
            return null;
        }

        String[] inputParts = userInput.split(" ");

        // make sure we have valid command input
        if (inputParts.length != 2 || !"send".equals(inputParts[0])) {
            return null;
        }

        return inputParts[1];
    }

    private static void SendMessage(String message) {

    }

    private static void UnreliableSend(DatagramSocket senderSocket, int toPort, char c) throws IOException {
        // all communication is on the same machine, so use local host
        InetAddress receiverAddress = InetAddress.getLocalHost();
        byte[] buffer = new byte[1];
        buffer[0] = (byte) c;
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, toPort);
        senderSocket.send(packet);
    }

    public static String UnreliableReceive(DatagramSocket receiverSocket) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receiverPacket);
        String fromIP = receiverPacket.getAddress().getHostAddress();
        int fromPort = receiverPacket.getPort();
        String msg = new String(buffer, 0, receiverPacket.getLength()).trim();
        return msg;
    }

    private static void InvalidUserInput() {
        System.err.println("Oops, I don't recognize that command, try again.");
    }

    private static void PrintSendPacket(int packetNum, char c) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " packet-" + packetNum + " " + c + " sent";
        System.out.println(toPrint);
    }

    // Receive Ack-1 refers to receving the ack but no window advancement occurs
    private static void PrintAck1(int packetNum) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " ACK-" + packetNum + " received";
        System.out.println(toPrint);
    }

    // window advancement occurs for Receive Ack-2, with starting/ending packet number of the window
    private static void PrintAck2(int packetNum, int windowStart, int windowEnd) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " ACK-" + packetNum + " received; window = [" + windowStart + "," + windowEnd + "]";
        System.out.println(toPrint);
    }

    private static void PrintTimeout(int packetNum) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " packet-" + packetNum + " timeout";
        System.out.println(toPrint);
    }

    private static void PrintReceive1(int packetNum, char c) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " packet-" + packetNum + " " + c + " received";
        System.out.println(toPrint);
    }

    private static void PrintReceive2(int packetNum, char c, int windowStart, int windowEnd) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " packet-" + packetNum + " " + c + " received; window = [" + windowStart + "," + windowEnd + "]";
        System.out.println(toPrint);
    }

    private static void PrintSendAck(int packetNum) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " ACK-" + packetNum + " sent";
        System.out.println(toPrint);
    }

    private static void PrintDiscardPacket(int packetNum, char c) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " packet-" + packetNum + " " + c + " discarded";
        System.out.println(toPrint);
    }

}
