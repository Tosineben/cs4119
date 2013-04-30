import java.io.*;
import java.net.*;
import java.util.*;

public class SRNode {

    public static void main(String[] args) {
        SRNode node;

        try {
            // get input arguments
            int sourcePort = Integer.parseInt(args[0]);
            int destPort = Integer.parseInt(args[1]);
            int windowSize = Integer.parseInt(args[2]);
            int timeoutMs = Integer.parseInt(args[3]);
            double lossRate = Double.parseDouble(args[4]);

            // make a node, which validates inputs, and kick off SR
            node = new SRNode(sourcePort, destPort, windowSize, timeoutMs, lossRate);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Usage: SRNode <source-port> <destination-port> <window-size> <time-out> <loss-rate>");
            return;
        }

        node.Initialize();
    }

    // *********************************************
    // ************** PRIVATE FIELDS ***************
    // *********************************************

    // private fields used by Selective Repeat
    private int sourcePort;
    private DatagramSocket socket;
    private int destPort;
    private int windowSize;
    private int timeoutMs;

    // private fields used by Selective Repeat SENDER
    private int sendNextSeqNum;
    private int sendWindowBase;
    private HashSet<Integer> ackedPackets = new HashSet<Integer>();
    private List<Integer> queuedPackets = new ArrayList<Integer>();
    private HashMap<Integer, Packet> sendPackets = new HashMap<Integer, Packet>();
    private HashMap<Integer, Long> inFlightPacketTimes = new HashMap<Integer, Long>();

    // private fields used by Select Repeat RECEIVER
    private int rcvWindowBase;
    private HashMap<Integer, Packet> rcvdPackets = new HashMap<Integer, Packet>();
    private double lossRate;

    public SRNode(int sourcePort, int destPort, int windowSize, int timeoutMs, double lossRate) throws IllegalArgumentException, SocketException {

        if (lossRate < 0 || lossRate >= 1 || sourcePort <= 0 || destPort <= 0 || windowSize <= 0 || timeoutMs <= 0) {
            throw new IllegalArgumentException("Arguments outside valid range.");
        }

        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.windowSize = windowSize;
        this.timeoutMs = timeoutMs;
        this.lossRate = lossRate;
        this.socket = new DatagramSocket(sourcePort);
    }

    // *********************************************
    // ***************** METHODS *******************
    // *********************************************

    // set up SRNode
    public void Initialize() {
        // listen for user input on another thread
        new Thread(new UserListener()).start();

        // listen for udp updates on this thread
        ListenForUpdates();
    }

    // listen for incoming udp messages
    private void ListenForUpdates() {
        // receive UDP messages forever
        while (true) {

            byte[] buffer = new byte[1024];
            DatagramPacket receivedDatagram = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(receivedDatagram);
            }
            catch (IOException e) {
                continue; // just swallow this, received a weird packet
            }

            // simulate packet loss, done by receiver based on https://piazza.com/class#spring2013/csee4119/155
            if (new Random().nextDouble() < lossRate) {
                continue;
            }

            int fromPort = receivedDatagram.getPort();
            String msg = new String(buffer, 0, receivedDatagram.getLength()).trim();

            if (msg.startsWith("ACK")) {
                int packetNum;
                try {
                    packetNum = Integer.parseInt(msg.split(",")[1]);
                }
                catch (Exception e) {
                    continue; // this should never happen, invalid ACK message
                }
                HandleReceivedAck(packetNum);
            }
            else {
                Packet p = new Packet(msg, fromPort, sourcePort);
                HandleReceived(p);
            }
        }
    }

    // when we receive an ACK, possibly shift window and possibly send
    // more packets if any are waiting to be sent
    private void HandleReceivedAck(int packetNum) {

        if (ackedPackets.contains(packetNum) || packetNum < sendWindowBase || packetNum >= sendWindowBase + windowSize) {
            // note, we can assume sender/receiver windows are the same so that this will never happen
            // see this post: https://piazza.com/class#spring2013/csee4119/152
            return;
        }

        // mark the packet as ACKed
        ackedPackets.add(packetNum);

        // if this is the first packet in the window, shift window and send more packets
        if (sendWindowBase == packetNum) {

            // shift the window up to the next unACKed packet
            while (ackedPackets.contains(sendWindowBase)) {
                sendWindowBase++;
            }

            // print the ACK2
            SenderPrinting.PrintAck2(packetNum, sendWindowBase, sendWindowBase + windowSize);

            // send all pending packets that are inside the new window
            while (!queuedPackets.isEmpty() && queuedPackets.get(0) < sendWindowBase + windowSize) {
                int nextPacketToSend = queuedPackets.remove(0);
                SendOnePacket(sendPackets.get(nextPacketToSend));
            }
        }
        else {
            // just print ACK1, don't move window or send anything new
            SenderPrinting.PrintAck1(packetNum);
        }

    }

    // when we receive a packet, possibly shift window and
    // deliver data to next layer up, and always send an ACK
    private void HandleReceived(Packet payload) {

        if (payload.Number >= rcvWindowBase + windowSize) {
            // this should never happen because we can assume sender/receiver windows are the same
            // see this post: https://piazza.com/class#spring2013/csee4119/152
            return;
        }

        // if the packet is before our window or we've received it, discard it
        if (payload.Number < rcvWindowBase || rcvdPackets.containsKey(payload.Number)) {
            ReceiverPrinting.PrintDiscardPacket(payload.Number, payload.Data);
        }
        else {
            // mark the packet received
            rcvdPackets.put(payload.Number, payload);

            // if this is the first packet in our window, shift window and deliver data (in theory)
            if (payload.Number == rcvWindowBase) {

                // shift the window up to the next packet we need
                while (rcvdPackets.containsKey(rcvWindowBase)) {

                    // ***** NOTE: THIS IS WHERE WE CAN GUARANTEE IN-ORDER DATA
                    // System.out.println("# DELIVER: " + rcvdPackets.get(rcvWindowBase).Data);

                    rcvWindowBase++;
                }

                // print Receive2
                ReceiverPrinting.PrintReceive2(payload.Number, payload.Data, rcvWindowBase, rcvWindowBase + windowSize);
            }
            else {
                // just print Receive1, don't shift window or deliver data
                ReceiverPrinting.PrintReceive1(payload.Number, payload.Data);
            }
        }

        // send an ACK no matter what
        UnreliableSend(payload.SourcePort, "ACK," + payload.Number);
        ReceiverPrinting.PrintSendAck(payload.Number);

    }

    // chop a message into characters and send each character as a packet
    private void SendMessage(final String message) {
        List<Packet> packets = new ArrayList<Packet>();
        for (char c : message.toCharArray()) {
            Packet payload = new Packet(Character.toString(c), sendNextSeqNum++, sourcePort, destPort);
            packets.add(payload);
        }
        SendPacketsImpl(packets);
    }

    // ensures that a set of packets is sent successfully
    // sends them once, then monitors for timeouts until
    // all packets are ACKed
    private void SendPacketsImpl(List<Packet> packets) {

        // send or queue all of the packets
        for (Packet payload : packets) {
            sendPackets.put(payload.Number, payload);

            // if the window is full, save it for later
            if (payload.Number >= sendWindowBase + windowSize) {
                queuedPackets.add(payload.Number);
            }
            else {
                SendOnePacket(payload);
            }
        }

        // wait for all packets to be ACKed and check for timeouts
        while (true) {

            // check for timeouts every 10ms
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // if the thread gets aborted, we're screwed
            }

            // if nothing is in flight, we're done!
            if (inFlightPacketTimes.isEmpty()) {
                break;
            }

            for (Integer packetNum : new ArrayList<Integer>(inFlightPacketTimes.keySet())) {

                // if the packet has been ACKed, no longer in flight
                if (ackedPackets.contains(packetNum)) {
                    inFlightPacketTimes.remove(packetNum);
                    continue;
                }

                // check for timeout
                if (inFlightPacketTimes.get(packetNum) + timeoutMs < Calendar.getInstance().getTimeInMillis()) {
                    // note that SDNode does not print anything when timeouts happen
                    SendOnePacket(sendPackets.get(packetNum));
                }
            }
        }

        // at this point, all packets have been ACKed, clear out send buffer
        sendPackets.clear();
    }

    // send a packet unreliably and update it's timestamp
    private void SendOnePacket(final Packet payload) {
        inFlightPacketTimes.put(payload.Number, Calendar.getInstance().getTimeInMillis());
        UnreliableSend(payload.DestPort, payload.toString());
        SenderPrinting.PrintSendPacket(payload.Number, payload.Data);
    }

    // send a message unreliably to a port
    private void UnreliableSend(int toPort, String message) {
        try {
            // all communication is on the same machine, so use local host
            InetAddress receiverAddress = InetAddress.getLocalHost();
            byte[] buffer = message.getBytes();
            DatagramPacket sendDatagram = new DatagramPacket(buffer, buffer.length, receiverAddress, toPort);
            socket.send(sendDatagram);
        }
        catch (IOException e) {
            // swallow this, we will resend if needed
        }
    }

    // *********************************************
    // ************** HELPER CLASSES ***************
    // *********************************************

    // thread that listens for user input and delegates
    // different commands to the SRNode
    private class UserListener implements Runnable {

        @Override
        public void run() {
            // accept user input forever
            while (true) {
                String message = GetMessageFromUser();
                if (message == null) {
                    UnrecognizedInput();
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

        private void UnrecognizedInput(){
            System.err.println("Oops, I don't recognize that command, try again.");
        }

    }

    // an individual packet to be sent with UDP
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
            SourcePort = sourcePort;
            DestPort = destPort;

            int separator = pcktAsString.indexOf('_');
            Number = Integer.parseInt(pcktAsString.substring(0, separator));
            Data = pcktAsString.substring(separator + 1);
        }

        @Override
        public String toString() {
            return Number + "_" + Data;
        }
    }

    // defines what we print for the sender component
    private static class SenderPrinting {

        public static void PrintSendPacket(int packetNum, String data) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] packet-" + packetNum + " " + data + " sent";
            System.out.println(toPrint);
        }

        // Receive Ack-1 refers to receiving the ack but no window advancement occurs
        public static void PrintAck1(int packetNum) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] ACK-" + packetNum + " received";
            System.out.println(toPrint);
        }

        // window advancement occurs for Receive Ack-2, with starting/ending packet number of the window
        public static void PrintAck2(int packetNum, int windowStart, int windowEnd) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] ACK-" + packetNum + " received; window = [" + windowStart + "," + windowEnd + "]";
            System.out.println(toPrint);
        }

        public static void PrintTimeout(int packetNum) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] packet-" + packetNum + " timeout";
            System.out.println(toPrint);
        }

    }

    // defines what we print for the receiver component
    private static class ReceiverPrinting {

        public static void PrintReceive1(int packetNum, String data) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] packet-" + packetNum + " " + data + " received";
            System.out.println(toPrint);
        }

        public static void PrintReceive2(int packetNum, String data, int windowStart, int windowEnd) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] packet-" + packetNum + " " + data + " received; window = [" + windowStart + "," + windowEnd + "]";
            System.out.println(toPrint);
        }

        public static void PrintSendAck(int packetNum) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] ACK-" + packetNum + " sent";
            System.out.println(toPrint);
        }

        public static void PrintDiscardPacket(int packetNum, String data) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] packet-" + packetNum + " " + data + " discarded";
            System.out.println(toPrint);
        }

    }

}
