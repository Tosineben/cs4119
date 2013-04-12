import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;

public class SRNode {

    public SRNode(int sourcePort, int destPort, int windowSize, int timeoutMs, double lossRate) throws IllegalArgumentException, SocketException {

        if (lossRate < 0 || lossRate > 1 || sourcePort <= 0 || destPort <= 0 || windowSize <= 0 || timeoutMs <= 0) {
            throw new IllegalArgumentException("Arguments outside valid range.");
        }

        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.windowSize = windowSize;
        this.timeoutMs = timeoutMs;
        this.lossRate = lossRate;

        this.socket = new DatagramSocket(sourcePort);

        this.sendNextSeqNum = 0;
        this.sendWindowBase = 0;
        this.ackedPackets = new HashMap<Integer, Packet>();
        this.dataToSend = new ArrayList<Packet>();

        this.rcvWindowBase = 0;
        this.rcvdPackets = new HashMap<Integer, Packet>();
    }

    private int sourcePort;     // send msgs from this port
    private int destPort;       // send msgs to this port
    private int windowSize;     // length of the ACK window
    private int timeoutMs;      // packet timeout
    private double lossRate;    // packet loss rate
    private DatagramSocket socket;

    private int sendNextSeqNum;
    private int sendWindowBase;
    private HashMap<Integer, Packet> ackedPackets;
    private List<Packet> dataToSend;

    private int rcvWindowBase;
    private HashMap<Integer, Packet> rcvdPackets;

    public static void main(String[] args) {

        // TODO remove this
        if (args.length == 0){
            args = new String[5];
            args[0] = "11111";
            args[1] = "22222";
            args[2] = "10";
            args[3] = "300";
            args[4] = "0.56";
        }

        SRNode node;

        try {
            // get input arguments
            int sourcePort = Integer.parseInt(args[0]);
            int destPort = Integer.parseInt(args[1]);
            int windowSize = Integer.parseInt(args[2]);
            int timeoutMs = Integer.parseInt(args[3]);
            double lossRate = Double.parseDouble(args[4]);

            // make a node, which validates inputs
            node = new SRNode(sourcePort, destPort, windowSize, timeoutMs, lossRate);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Usage: SRNode <source-port> <destination-port> <window-size> <time-out> <loss-rate>");
            return;
        }

        // kick off selective repeat
        node.StartSelectiveRepeat();
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
                    System.err.println("Oops, I don't recognize that command, try again.");
                    continue;
                }
                SendMessage(message);
            }
        }

        private String GetMessageFromUser() {

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            String userInput;

            // read from std in
            try {
                userInput = br.readLine();
            } catch (IOException e) {
                return null;
            }

            // now make sure it is a valid "send" command, and parse out the message

            int commandSeparator = userInput.indexOf(' ');

            if (commandSeparator < 0) {
                return null;
            }

            String command = userInput.substring(0, commandSeparator);
            String message = userInput.substring(commandSeparator + 1);

            if (!"send".equals(command)) {
                return null;
            }

            return message;
        }

    }

    private class UdpListener implements Runnable {
        @Override
        public void run() {
            // receive UDP messages forever
            while (true) {

                Packet received;
                try {
                    received = UnreliableReceive();
                }
                catch (IOException e) {
                    continue; // just swallow this, received a weird packet
                }

                if (received == null) {
                    continue; // this means the packet was "lost"
                }

                if (received.IsAck) {
                    HandleReceivedAck(received);
                }
                else {
                    HandleReceived(received);
                }
            }
        }
    }

    private class PacketSender implements Runnable {

        private Packet payload;

        public PacketSender(Packet payload) {
            this.payload = payload;
        }

        @Override
        public void run() {
            // loop forever until packet is ACKed
            while (true) {

                // send it unreliably
                try {
                    UnreliableSend(destPort, payload);
                }
                catch (IOException e) {
                    // swallow this, we will resend if no ACK received
                }

                // print that we sent it
                SenderPrinting.PrintSendPacket(payload.Number, payload.Data);

                // sleep for the timeout
                try {
                    Thread.sleep(timeoutMs);
                } catch (InterruptedException e) {
                    // swallow this, if the thread is aborted we're screwed
                }

                // if it's been ACKed, we're done, otherwise print timeout
                if (ackedPackets.containsKey(payload.Number)) {
                    break;
                }
                else {
                    SenderPrinting.PrintTimeout(payload.Number);
                }
            }
        }
    }

    private class Packet {
        public final int SourcePort;
        public final int DestPort;
        public final String Data;
        public final int Number;
        public final boolean IsAck;

        public Packet(String data, int number, int sourcePort, int destPort, boolean isAck) {
            Data = data;
            Number = number;
            SourcePort = sourcePort;
            DestPort = destPort;
            IsAck = isAck;
        }

        public Packet(String pcktAsString, int sourcePort, int destPort) {
            SourcePort = sourcePort;
            DestPort = destPort;

            int separator = pcktAsString.indexOf('_');
            String firstPart = pcktAsString.substring(0, separator);
            String secondPart = pcktAsString.substring(separator + 1);

            if ("ACK".equals(firstPart)) {
                IsAck = true;
                Number = Integer.parseInt(secondPart);
                Data = null;
            }
            else {
                IsAck = false;
                Number = Integer.parseInt(firstPart);
                Data = secondPart;
            }
        }

        @Override
        public String toString() {
            if (IsAck) {
                return "ACK_" + Number; // ACK packets have special prefix to identify them
            }
            else {
                return Number + "_" + Data;
            }
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

    private Packet UnreliableReceive() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket receivedDatagram = new DatagramPacket(buffer, buffer.length);
        socket.receive(receivedDatagram);

        // simulate packet loss, done by receiver based on https://piazza.com/class#spring2013/csee4119/155
        if (new Random().nextDouble() < lossRate) {
            return null;
        }

        String msg = new String(buffer, 0, receivedDatagram.getLength()).trim();
        return new Packet(msg, receivedDatagram.getPort(), sourcePort);
    }

    private void UnreliableSend(int toPort, Packet payload) throws IOException {
        // all communication is on the same machine, so use local host
        InetAddress receiverAddress = InetAddress.getLocalHost();
        byte[] buffer = payload.toString().getBytes();
        DatagramPacket sendDatagram = new DatagramPacket(buffer, buffer.length, receiverAddress, toPort);
        socket.send(sendDatagram);
    }

    private void HandleReceivedAck(Packet packet) {

        // TODO remove this
        if (ackedPackets.containsKey(packet.Number) || packet.Number < sendWindowBase || packet.Number >= sendWindowBase + windowSize) {
            System.out.println("AHHHHHHHHHHH");
            System.out.println("packet " + packet.Number + ", sendWindowBase " + sendWindowBase);
            return;
        }

        // mark the packet as ACKed
        ackedPackets.put(packet.Number, packet);

        // if this is the first packet in the window, shift window and send more packets
        if (sendWindowBase == packet.Number) {

            // shift the window up to the next unACKed packet
            while (ackedPackets.containsKey(sendWindowBase)) {
                sendWindowBase++;
            }

            // print the ACK2
            SenderPrinting.PrintAck2(packet.Number, sendWindowBase, sendWindowBase + windowSize);

            // send all pending packets that are inside the new window
            while (!dataToSend.isEmpty() && dataToSend.get(0).Number < sendWindowBase + windowSize) {
                Packet nextPacketToSend = dataToSend.remove(0);
                SendPacketOnNewThread(nextPacketToSend);
            }
        }
        else {
            // just print ACK1, don't move window or send anything new
            SenderPrinting.PrintAck1(packet.Number);
        }

    }

    private void HandleReceived(Packet payload) {

        // TODO remove this
        if (payload.Number >= rcvWindowBase + windowSize) {
            System.out.println("AHHHHHH");
            System.out.println("packet " + payload.Number + ", rcvWindowBase " + rcvWindowBase);
            return;
        }

        // if the packet is before our window, discard and resend ACK
        if (payload.Number < rcvWindowBase) {
            ReceiverPrinting.PrintDiscardPacket(payload.Number, payload.Data);
            SendAck(payload.Number, payload.SourcePort);
            return;
        }

        // if we have already received the packet, discard and resend ACK
        if (rcvdPackets.containsKey(payload.Number)) {
            ReceiverPrinting.PrintDiscardPacket(payload.Number, payload.Data);
            SendAck(payload.Number, payload.SourcePort);
            return;
        }

        // mark the packet received
        rcvdPackets.put(payload.Number, payload);

        // if this is the first packet in our window, shift window and deliver data (in theory)
        if (payload.Number == rcvWindowBase) {

            // shift the window up to the next packet we need
            while (rcvdPackets.containsKey(rcvWindowBase)) {

                // TODO remove this
                System.out.println("MESSAGE: " + rcvdPackets.get(rcvWindowBase).Data);

                rcvWindowBase++;
            }

            // print Receive2
            ReceiverPrinting.PrintReceive2(payload.Number, payload.Data, rcvWindowBase, rcvWindowBase + windowSize);
        }
        else {
            // just print Receive1, don't shift window or deliver data
            ReceiverPrinting.PrintReceive1(payload.Number, payload.Data);
        }

        // send an ACK
        SendAck(payload.Number, payload.SourcePort);

    }

    private void SendAck(int packetNum, int toPort) {

        // print that we're sending an ACK
        ReceiverPrinting.PrintSendAck(packetNum);

        // make the ACK packet
        Packet ackPacket = new Packet(null, packetNum, sourcePort, toPort, true);

        // send it unreliably
        try {
            UnreliableSend(toPort, ackPacket);
        }
        catch (IOException e) {
            // swallow this, ACK will be resent if necessary
        }
    }

    private void SendMessage(final String message) {

        // send one character at a time
        for (char c : message.toCharArray()) {

            final Packet payload = new Packet(Character.toString(c), sendNextSeqNum, sourcePort, destPort, false);

            // if the window is full, save it for later
            if (sendNextSeqNum >= sendWindowBase + windowSize) {
                dataToSend.add(payload);
            }
            else {
                SendPacketOnNewThread(payload);
            }

            // increment sequence number
            sendNextSeqNum++;
        }
    }

    private void SendPacketOnNewThread(final Packet payload) {
        new Thread(new PacketSender(payload)).start();
        try {
            Thread.sleep(2); // sleep really quickly to force window packets to be in order
        }
        catch (Exception e){}
    }

}
