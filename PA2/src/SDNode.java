import java.io.*;
import java.net.*;
import java.util.*;

public class SDNode {

    public static void main(String[] args) {

        SDNode node;
        boolean last;

        try {
            int port = Integer.parseInt(args[0]);

            HashMap<Integer, Double> neighbors = new HashMap<Integer, Double>();

            // count args by two's, getting info for each neighbor
            for (int i = 1; i < args.length - 1; i += 2) {
                int nPort = Integer.parseInt(args[i]);
                double nLossRate = Double.parseDouble(args[i + 1]);

                if (nPort <= 0 || nLossRate < 0 || nLossRate > 1) {
                    throw new IllegalArgumentException("Arguments outside valid range.");
                }

                neighbors.put(nPort, nLossRate);
            }

            node = new SDNode(port, neighbors);
            last = "last".equals(args[args.length - 1]);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Usage: <port-number> <neighbor1port> <neighbor1lossrate> .... <neighboriport> <neighborilossrate> [last]?");
            return;
        }

        node.Initialize(last);

    }

    private SRNode selectiveRepeat;
    private final int port;
    private boolean sentBroadcast;
    private HashMap<Integer, RoutingTableEntry> routingTable;
    private HashMap<Integer, Neighbor> neighbors;

    public SDNode(int port, HashMap<Integer, Double> neighbors) throws SocketException {
        this.port = port;
        this.neighbors = new HashMap<Integer, Neighbor>();
        this.routingTable = new HashMap<Integer, RoutingTableEntry>();

        // defaults from assignment description: windowSize = 10, timeout = 300ms
        this.selectiveRepeat = new SRNode(port, 10, 300);

        for(Map.Entry<Integer, Double> neighbor : neighbors.entrySet()) {
            Neighbor n = new Neighbor(neighbor.getKey(), neighbor.getValue());
            this.neighbors.put(n.Port, n);
        }
    }

    // info we need to store about each neighbor to compute routing table
    private class Neighbor {
        public final int Port;
        public double Weight;
        public double LossRate;
        public HashMap<Integer, RoutingTableEntry> Routes;

        public Neighbor(int port, double lossRate) {
            Port = port;
            LossRate = (double)Math.round(lossRate * 1000)/1000; // round to 3 decimal places
            double weight = 1 / (1 - LossRate);
            Weight = (double)Math.round(weight * 1000)/1000; // round to 3 decimal places
            Routes = new HashMap<Integer, RoutingTableEntry>();
        }
    }

    // routing used by this node
    private class RoutingTableEntry {
        public final int ToPort;
        public int NeighborPort;
        public final double Weight;

        public RoutingTableEntry(int toPort, int neighborPort, double weight) {
            ToPort = toPort;
            NeighborPort = neighborPort;
            Weight = (double)Math.round(weight * 1000)/1000; // round to 3 decimal places
        }
    }

    public void Initialize(boolean isLast) {
        // set up the routing table
        EnsureRoutingTableIsUpdated();

        // print the routing table
        DvPrinting.PrintRoutingTable(port, routingTable);

        // start broadcast if we're last
        if (isLast) {
            Broadcast();
        }

        // listen for user input on another thread
        new Thread(new UserListener()).start();

        // tell SRNode to listen for incoming updates
        selectiveRepeat.ListenForUpdates();
    }

    private void ChangeCommand(HashMap<Integer, Double> updatedNeighbors) {
        // ignore changes to non-neighbors

        //Once a node is informed changes on the link, it ﬁrst send an link
        // update packet (contain the new link weight)
        //to all neighbours who shares the link. When its neighbours
        // receive these packets, they update the link weights
        //and send ACK back to the node.
        // When the node receives all ACK from neighbours that update
        // the link weight, it start DV of the system if its routing table changes.

    }

    private void SendCommand(int destPort, int numPackets) {

    }

    private class UserListener implements Runnable {

        @Override
        public void run() {
            // accept user input forever
            while (true) {

                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

                String userInput;
                try {
                    userInput = br.readLine();
                } catch (IOException e) {
                    UnrecognizedInput();
                    continue;
                }

                int commandSeparator = userInput.indexOf(' ');

                if (commandSeparator < 0) {
                    UnrecognizedInput();
                    continue;
                }

                String command = userInput.substring(0, commandSeparator);
                String message = userInput.substring(commandSeparator + 1);
                String[] msgParts = message.split(" ");

                if ("change".equals(command)) {
                    HandleChange(msgParts);
                }
                else if ("send".equals(command)) {
                    HandleSend(msgParts);
                }
                else {
                    UnrecognizedInput();
                }
            }
        }

        private void HandleChange(String[] msgParts){
            HashMap<Integer, Double> updatedNeighbors = new HashMap<Integer, Double>();

            try {
                // count args by two's, getting info for each neighbor
                for (int i = 1; i < msgParts.length - 1; i += 2) {
                    int nPort = Integer.parseInt(msgParts[i]);
                    double nLossRate = Double.parseDouble(msgParts[i + 1]);

                    if (nPort <= 0 || nLossRate < 0 || nLossRate > 1) {
                        throw new IllegalArgumentException("Arguments outside valid range.");
                    }

                    updatedNeighbors.put(nPort, nLossRate);
                }
            }
            catch (Exception e) {
                UnrecognizedInput();
                return;
            }

            ChangeCommand(updatedNeighbors);
        }

        private void HandleSend(String[] msgParts){
            int destPort;
            int numPackets;

            try {
                destPort = Integer.parseInt(msgParts[0]);
                numPackets = Integer.parseInt(msgParts[1]);
            }
            catch (Exception e){
                UnrecognizedInput();
                return;
            }

            SendCommand(destPort, numPackets);
        }

        private void UnrecognizedInput(){
            System.err.println("Oops, I don't recognize that command, try again.");
        }

    }

    private void HandleUpdate(int fromPort, String message) {

        // print that we received
        DvPrinting.PrintRcvMessage(port, fromPort);

        // if we're receiving a message from a non-neighbor, ignore it
        // note, this should never happen, just a safety check
        if (!neighbors.containsKey(fromPort)) {
            return;
        }

        HashMap<Integer, RoutingTableEntry> neighborRoutingTable = new HashMap<Integer, RoutingTableEntry>();

        // parse the message into the neighbors routing table
        try {
            for (String entryString : message.split(" ")) {
                String[] entryParts = entryString.split(",");

                int toPort = Integer.parseInt(entryParts[0]);
                int neighborPort = Integer.parseInt(entryParts[1]);
                double roundedWeight = Double.parseDouble(entryParts[2]);

                neighborRoutingTable.put(toPort, new RoutingTableEntry(toPort, neighborPort, roundedWeight));
            }
        }
        catch (Exception e) {
            // received an improperly formatted message, which should never happen - just ignore it
            return;
        }

        // update the neighbors routing table
        neighbors.get(fromPort).Routes = neighborRoutingTable;

        // update routing table - if it changed print and broadcast
        if (EnsureRoutingTableIsUpdated()) {
            DvPrinting.PrintRoutingTable(port, routingTable);
            Broadcast();
        }
        else if (!sentBroadcast) { // we need to broadcast at least once or DV initialization fails
            Broadcast();
        }
    }

    // updates routing table based on neighbor info, returns true if table changed
    private boolean EnsureRoutingTableIsUpdated() {

        HashMap<Integer, RoutingTableEntry> newRoutingTable = new HashMap<Integer, RoutingTableEntry>();

        // initialize routing table with direct neighbor links
        for (Neighbor neighbor : neighbors.values()) {
            RoutingTableEntry entry = new RoutingTableEntry(neighbor.Port, neighbor.Port, neighbor.Weight);
            newRoutingTable.put(entry.ToPort, entry);
        }

        // examine each neighbors routes and update ours accordingly
        for (Neighbor neighbor : neighbors.values()) {
            for (RoutingTableEntry neighborEntry : neighbor.Routes.values()) {
                RoutingTableEntry newEntry = new RoutingTableEntry(neighborEntry.ToPort, neighbor.Port, neighborEntry.Weight + neighbor.Weight);

                // if we don't have this route yet, add it
                if (!newRoutingTable.containsKey(newEntry.ToPort)) {
                    newRoutingTable.put(newEntry.ToPort, newEntry);
                }
                else { // otherwise only add it if it's better
                    RoutingTableEntry existingEntry = newRoutingTable.get(neighborEntry.ToPort);
                    if (newEntry.Weight < existingEntry.Weight) {
                        newRoutingTable.put(newEntry.ToPort, newEntry);
                    }
                }
            }
        }

        // see if there are any differences between our new and old routing table
        boolean updated = false;
        for (RoutingTableEntry newEntry : newRoutingTable.values()) {
            if (!routingTable.containsKey(newEntry.ToPort)) {
                updated = true;
                break;
            }
            RoutingTableEntry existingEntry = routingTable.get(newEntry.ToPort);
            if (existingEntry.NeighborPort != newEntry.NeighborPort || existingEntry.Weight != newEntry.Weight) {
                updated = true;
                break;
            }
        }

        routingTable = newRoutingTable;
        return updated;
    }

    private String ConstructMessageForNeighbor(int neighborPort) {

        // I am defining the broadcast message as follows:
        // <reachable-node1>,<next-node1>,<weight1> <reachable-node2>,<next-node2>,<weight2> ...

        String message = "";
        for (RoutingTableEntry entry : routingTable.values()) {
            // only tell neighbor we can get to places that don't go through them
            if (entry.NeighborPort != neighborPort && entry.ToPort != neighborPort){
                message += entry.ToPort + "," + entry.NeighborPort + "," + entry.Weight + " ";
            }
        }
        return message;
    }

    private void Broadcast() {
        for (int neighborPort : neighbors.keySet()) {
            String message = ConstructMessageForNeighbor(neighborPort);

            // send it with selective repeat
            selectiveRepeat.SendMessage(neighborPort, message);

            DvPrinting.PrintSendMessage(port, neighborPort);
        }

        // mark that we have sent at least 1 broadcast
        sentBroadcast = true;
    }

    // this is NOT exactly the same as SRNode from part 1
    // it is similar, but tweaks have been made to fit needs of SDNode
    public class SRNode {

        public SRNode(int sourcePort, int windowSize, int timeoutMs) throws IllegalArgumentException, SocketException {
            this.sourcePort = sourcePort;
            this.windowSize = windowSize;
            this.timeoutMs = timeoutMs;

            this.socket = new DatagramSocket(sourcePort);

            this.sendNextSeqNum = 0;
            this.sendWindowBase = 0;
            this.ackedPackets = new HashSet<Integer>();
            this.queuedPackets = new ArrayList<Packet>();

            this.rcvWindowBase = 0;
            this.rcvdPackets = new HashMap<Integer, Packet>();
        }

        private int sourcePort;     // send msgs from this port
        private int windowSize;     // length of the ACK window
        private int timeoutMs;      // packet timeout
        private DatagramSocket socket;

        private int sendNextSeqNum;
        private int sendWindowBase;
        private HashSet<Integer> ackedPackets;
        private List<Packet> queuedPackets;

        private int rcvWindowBase;
        private HashMap<Integer, Packet> rcvdPackets;

        public void ListenForUpdates() {
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

                int fromPort = receivedDatagram.getPort();
                String msg = new String(buffer, 0, receivedDatagram.getLength()).trim();

                // if we don't recognize the port, skip it
                if (!neighbors.containsKey(fromPort)){
                    continue;
                }

                // simulate packet loss, done by receiver based on https://piazza.com/class#spring2013/csee4119/155
                if (new Random().nextDouble() < neighbors.get(fromPort).LossRate) {
                    continue;
                }

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

        private class TimeoutListener implements Runnable {

            private Packet payload;

            public TimeoutListener(Packet payload) {
                this.payload = payload;
            }

            @Override
            public void run() {
                // loop forever until packet is ACKed
                while (true) {

                    // sleep for the timeout
                    try {
                        Thread.sleep(timeoutMs);
                    } catch (InterruptedException e) {
                        // swallow this, if the thread is aborted we're screwed
                    }

                    // if it's been ACKed, we're done, otherwise print timeout
                    if (ackedPackets.contains(payload.Number)) {
                        break;
                    }
                    else {
                        SrSenderPrinting.PrintTimeout(payload.Number);
                    }

                    // send it unreliably
                    UnreliableSend(payload.DestPort, payload.toString());
                    SrSenderPrinting.PrintSendPacket(payload.Number, payload.Data);
                }
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
                SrSenderPrinting.PrintAck2(packetNum, sendWindowBase, sendWindowBase + windowSize);

                // send all pending packets that are inside the new window
                while (!queuedPackets.isEmpty() && queuedPackets.get(0).Number < sendWindowBase + windowSize) {
                    Packet nextPacketToSend = queuedPackets.remove(0);
                    SendPacket(nextPacketToSend);
                }
            }
            else {
                // just print ACK1, don't move window or send anything new
                SrSenderPrinting.PrintAck1(packetNum);
            }

        }

        private void HandleReceived(Packet payload) {

            if (payload.Number >= rcvWindowBase + windowSize) {
                // this should never happen because we can assume sender/receiver windows are the same
                // see this post: https://piazza.com/class#spring2013/csee4119/152
                return;
            }

            // if the packet is before our window or we've received it, discard it
            if (payload.Number < rcvWindowBase || rcvdPackets.containsKey(payload.Number)) {
                SrReceiverPrinting.PrintDiscardPacket(payload.Number, payload.Data);
            }
            else {
                // mark the packet received
                rcvdPackets.put(payload.Number, payload);

                // if this is the first packet in our window, shift window and deliver data (in theory)
                if (payload.Number == rcvWindowBase) {

                    // deliver data and shift the window up to the next packet we need
                    while (rcvdPackets.containsKey(rcvWindowBase)) {
                        Packet toDeliver = rcvdPackets.get(rcvWindowBase);
                        HandleUpdate(toDeliver.SourcePort, toDeliver.Data);
                        rcvWindowBase++;
                    }

                    // print Receive2
                    SrReceiverPrinting.PrintReceive2(payload.Number, payload.Data, rcvWindowBase, rcvWindowBase + windowSize);
                }
                else {
                    // just print Receive1, don't shift window or deliver data
                    SrReceiverPrinting.PrintReceive1(payload.Number, payload.Data);
                }
            }

            // send an ACK no matter what
            UnreliableSend(payload.SourcePort, "ACK," + payload.Number);
            SrReceiverPrinting.PrintSendAck(payload.Number);

        }

        public void SendMessage(final int destPort, final String message) {
            Packet payload = new Packet(message, sendNextSeqNum, sourcePort, destPort);

            // if the window is full, save it for later
            if (sendNextSeqNum >= sendWindowBase + windowSize) {
                queuedPackets.add(payload);
            }
            else {
                SendPacket(payload);
            }

            // increment sequence number
            sendNextSeqNum++;
        }

        private void SendPacket(final Packet payload) {
            SrSenderPrinting.PrintSendPacket(payload.Number, payload.Data);
            UnreliableSend(payload.DestPort, payload.toString());
            new Thread(new TimeoutListener(payload)).start();
        }

    }

    private static class DvPrinting {

        public static void PrintSendMessage(int sourceNodePort, int destNodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " Message sent from Node " + sourceNodePort + " to Node " + destNodePort;
            System.out.println(toPrint);
        }

        public static void PrintRcvMessage(int destNodePort, int sourceNodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " Message received at Node " + destNodePort + " from Node " + sourceNodePort;
            System.out.println(toPrint);
        }

        public static void PrintRoutingTable(int nodePort, HashMap<Integer, RoutingTableEntry> routingTable) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " Node " + nodePort + " - Routing Table";
            for (RoutingTableEntry entry : routingTable.values()) {
                if (entry.ToPort == entry.NeighborPort) {
                    toPrint += "\nNode " + entry.ToPort + " -> (" + entry.Weight + ")";
                }
                else {
                    toPrint += "\nNode " + entry.ToPort + " [next " + entry.NeighborPort + "] -> (" + entry.Weight + ")";
                }
            }
            System.out.println(toPrint);
        }

    }

    private static class SrSenderPrinting {

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

    private static class SrReceiverPrinting {

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

}
