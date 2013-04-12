import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;

public class SRNode {

    public SRNode(int sourcePort, int destPort, int windowSize, int timeoutMs, double lossRate) throws SocketException {
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.windowSize = windowSize;
        this.timeoutMs = timeoutMs;
        this.lossRate = lossRate;

        this.socket = new DatagramSocket(sourcePort);

        this.sendNextSeqNum = 0;
        this.sendWindowBase = 0;
        this.sendWindow = new Packet[windowSize];
        this.ackedPackets = new HashSet<Integer>();

        this.rcvWindowBase = 0;
        this.rcvWindow = new Packet[windowSize];
    }

    private int sourcePort;     // send msgs from this port
    private int destPort;       // receive msgs from this port
    private int windowSize;     // length of the ACK window
    private int timeoutMs;      // packet timeout
    private double lossRate;    // packet loss rate
    private DatagramSocket socket;

    private int sendNextSeqNum;
    private int sendWindowBase;
    private Packet[] sendWindow;
    private HashSet<Integer> ackedPackets = new HashSet<Integer>();
    private List<Packet> dataToSend = new ArrayList<Packet>();

    private int rcvWindowBase;
    private Packet[] rcvWindow;
    private HashSet<Integer> rcvdPackets = new HashSet<Integer>();

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

        try {
            // get arguments
            int sourcePort = Integer.parseInt(args[0]);
            int destPort = Integer.parseInt(args[1]);
            int windowSize = Integer.parseInt(args[2]);
            int timeoutMs = Integer.parseInt(args[3]);
            double lossRate = Double.parseDouble(args[4]);

            // make a node
            SRNode node = new SRNode(sourcePort, destPort, windowSize, timeoutMs, lossRate);

            // kick off selective repeat
            node.StartSelectiveRepeat();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Usage: SRNode <source-port> <destination-port> <window-size> <time-out> <loss-rate>");
        }
    }

    public void StartSelectiveRepeat() {
        new Thread(new UdpListener()).start();
        new Thread(new UserListener()).start();
    }

    private class UserListener implements Runnable {
        @Override
        public void run() {
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
    }

    private class UdpListener implements Runnable {
        @Override
        public void run() {
            // receive UDP messages forever
            while (true) {

                Packet received;
                try {
                    received = new UnreliableUdp().Receive(socket);
                }
                catch (IOException e) {
                    // swallow this, received a weird packet
                    continue;
                }

                if ("ACK".equals(received.Data)) {
                    HandleReceivedAck(received.Number);
                }
                else {
                    HandleReceived(received);
                }

            }
        }
    }

    private class UnreliableUdp {

        public Packet Receive(DatagramSocket receiverSocket) throws IOException {
            byte[] buffer = new byte[1024];
            DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(receiverPacket);
            String msg = new String(buffer, 0, receiverPacket.getLength()).trim();
            return new Packet(msg, receiverPacket.getPort(), receiverSocket.getPort());
        }

        private void Send(DatagramSocket senderSocket, int toPort, Packet payload) throws IOException {
            // all communication is on the same machine, so use local host
            InetAddress receiverAddress = InetAddress.getLocalHost();
            byte[] buffer = payload.toString().getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, toPort);
            senderSocket.send(packet);
        }

    }

    private class Packet {
        public final int SourcePort;
        public final int DestPort;
        public final String Data;
        public final int Number;

        public Packet(String data, int number, int sourcePort, int destPort) {
            Data = data;
            Number = number;
            SourcePort = sourcePort;
            DestPort = destPort;
        }

        public Packet(String pcktAsString, int sourcePort, int destPort) {
            int separator = pcktAsString.indexOf('_');
            Number = Integer.parseInt(pcktAsString.substring(0, separator));
            Data = pcktAsString.substring(separator);
            SourcePort = sourcePort;
            DestPort = destPort;
        }

        @Override
        public String toString() {
            return Number + "_" + Data;
        }
    }

    private static class SenderPrinting {

        public static void PrintSendPacket(int packetNum, String data) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " packet-" + packetNum + " " + data + " sent";
            System.out.println(toPrint);
        }

        // Receive Ack-1 refers to receiving the ack but no window advancement occurs
        public static void PrintAck1(int packetNum) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " ACK-" + packetNum + " received";
            System.out.println(toPrint);
        }

        // window advancement occurs for Receive Ack-2, with starting/ending packet number of the window
        public static void PrintAck2(int packetNum, int windowStart, int windowEnd) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " ACK-" + packetNum + " received; window = [" + windowStart + "," + windowEnd + "]";
            System.out.println(toPrint);
        }

        public static void PrintTimeout(int packetNum) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " packet-" + packetNum + " timeout";
            System.out.println(toPrint);
        }

    }

    private static class ReceiverPrinting {

        public static void PrintReceive1(int packetNum, String data) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " packet-" + packetNum + " " + data + " received";
            System.out.println(toPrint);
        }

        public static void PrintReceive2(int packetNum, String data, int windowStart, int windowEnd) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " packet-" + packetNum + " " + data + " received; window = [" + windowStart + "," + windowEnd + "]";
            System.out.println(toPrint);
        }

        public static void PrintSendAck(int packetNum) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " ACK-" + packetNum + " sent";
            System.out.println(toPrint);
        }

        public static void PrintDiscardPacket(int packetNum, String data) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " packet-" + packetNum + " " + data + " discarded";
            System.out.println(toPrint);
        }

    }

    private void HandleReceivedAck(int packetNum) {
        if (sendWindowBase == packetNum) {
            // shift the window
            sendWindowBase++;

            // print that we received ACK2
            SenderPrinting.PrintAck2(packetNum, sendWindowBase, sendWindowBase + windowSize);

            // send the next packet
            if (!dataToSend.isEmpty()) {
                Packet nextPacketToSend = dataToSend.remove(0);
                EnsurePacketIsSent(nextPacketToSend);
            }
        }
        else {
            // print that we received ACK1
            SenderPrinting.PrintAck1(packetNum);

            // do not shift window or send any additional packets
        }

        // mark the packet as ACKed
        ackedPackets.add(packetNum);
    }

    private void HandleReceived(Packet payload) {

        // if the packet is before our window, discard and resend ACK
        if (payload.Number < rcvWindowBase) {
            ReceiverPrinting.PrintDiscardPacket(payload.Number, payload.Data);
            SendAck(payload.Number);
            return;
        }

        // if we have already received the packet, discard and resend ACK
        if (rcvdPackets.contains(payload.Number)) {
            ReceiverPrinting.PrintDiscardPacket(payload.Number, payload.Data);
            SendAck(payload.Number);
            return;
        }

        // if this is the packet at the bottom of our window, shift it
        if (payload.Number == rcvWindowBase) {
            rcvWindowBase++;
            ReceiverPrinting.PrintReceive2(payload.Number, payload.Data, rcvWindowBase, rcvWindowBase + windowSize);
        }
        else {
            ReceiverPrinting.PrintReceive1(payload.Number, payload.Data);
        }

        // mark the packet received
        rcvdPackets.add(payload.Number);
    }

    private void SendAck(int packetNum) {
        ReceiverPrinting.PrintSendAck(packetNum);
    }

    private void SendMessage(final String message) {

        // send one character at a time
        for (char c : message.toCharArray()) {

            final Packet payload = new Packet(Character.toString(c), sendNextSeqNum++, sourcePort, destPort);

            // if the window is full, save it for later
            if (sendNextSeqNum >= sendWindowBase + windowSize) {
                dataToSend.add(payload);
            }
            else {
                EnsurePacketIsSent(payload);
            }

        }
    }

    private void EnsurePacketIsSent(final Packet payload) {

        ExecutorService executor = Executors.newSingleThreadExecutor();

        // while the packet is not ACKed, loop forever
        while (!ackedPackets.contains(payload.Number)) {

            // send it unreliably
            try {
                new UnreliableUdp().Send(socket, destPort, payload);
            }
            catch (IOException e) {
                // swallow this, we will resend if no ACK received
            }

            // print that we sent it
            SenderPrinting.PrintSendPacket(payload.Number, payload.Data);

            // on another thread, wait for ACK or timeout
            Future<Void> waitForAckFuture = executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    while (!ackedPackets.contains(payload.Number)) {}
                    return null;
                }
            });

            try {
                waitForAckFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            }
            catch (Exception e) {
                // print that we timed out
                SenderPrinting.PrintTimeout(payload.Number);
            }
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

    private static void InvalidUserInput() {
        System.err.println("Oops, I don't recognize that command, try again.");
    }

}
