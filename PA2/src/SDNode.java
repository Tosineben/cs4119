import java.io.*;
import java.net.*;
import java.util.*;

public class SDNode {

    // GOOD TO GO
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

                if (nPort <= 0 || nLossRate < 0) {
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

    private UdpListener udpListener;
    private final int sourcePort;
    private boolean sentBroadcast;
    private HashMap<Integer, RoutingTableEntry> routingTable = new HashMap<Integer, RoutingTableEntry>();
    private HashMap<Integer, Neighbor> neighbors = new HashMap<Integer, Neighbor>();

    private HashMap<String, SendStat> sendStatistics = new HashMap<String, SendStat>();
    private SendCommand currentSend;

    public SDNode(int port, HashMap<Integer, Double> neighbors) throws SocketException {
        this.sourcePort = port;

        for(Map.Entry<Integer, Double> neighbor : neighbors.entrySet()) {
            Neighbor n = new Neighbor(neighbor.getKey(), neighbor.getValue());
            this.neighbors.put(n.Port, n);
        }

        this.udpListener = new UdpListener(port);
    }

    // info we need to store about each neighbor to compute routing table
    private class Neighbor {
        public final int Port;
        public double Weight;
        public double LossRate;
        public HashMap<Integer, RoutingTableEntry> Routes = new HashMap<Integer, RoutingTableEntry>();
        public SRNode SrNode;

        public Neighbor(int port, double lossRate) throws SocketException {
            Port = port;

            // defaults from assignment description: windowSize = 10, timeout = 300ms
            SrNode = new SRNode(sourcePort, port, 10, 300);

            UpdateLossRate(lossRate);
        }

        public void UpdateLossRate(double lossRate) {
            LossRate = (double)Math.round(lossRate * 1000)/1000; // round to 3 decimal places
            double weight = 1 / (1 - LossRate);
            Weight = (double)Math.round(weight * 1000)/1000; // round to 3 decimal places
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

    // statistics to keep track of "send"
    private class SendStat {

        public SendStat(String message, int numReceived, int numDropped) {
            Message = message;
            NumReceived = numReceived;
            NumDropped = numDropped;
        }

        public String Message;
        public int NumReceived;
        public int NumDropped;

        public int GetTotalPackets() {
            return NumReceived + NumDropped;
        }

        public double GetLossRate() {
            double lossRate = NumDropped / GetTotalPackets();
            return (double)Math.round(lossRate * 1000)/1000; // round to 3 decimal places
        }

    }

    // information about current "send"
    private class SendCommand {

        public SendCommand(int finalDestPort, int numPackets) {
            FinalDestPort = finalDestPort;
            NumPackets = numPackets;
        }

        public int NeighborPort;
        public int FinalDestPort;
        public int NumPackets;
        public long StartTime;

        public List<Integer> NeighborsToSend = new ArrayList<Integer>();

    }

    // helpers for defining packet payloads
    private static final String PREFIX_DELIM = "_";
    private static final String CHANGE_PREFIX = "CHANGE";
    private static final String BROADCAST_PREFIX = "DV";
    private static final String SEND_PREFIX = "SEND";
    private static final String END_OF_SEND_PREFIX = "ENDSEND";

    // GOOD TO GO
    public void Initialize(boolean isLast) {

        // set up the routing table
        EnsureRoutingTableIsUpdated();

        // print the routing table
        DvPrinting.PrintRoutingTable(sourcePort, routingTable);

        // listen for incoming udp on another thread, do this before broadcast
        new Thread(udpListener).start();

        // start broadcast if we're last
        if (isLast) {
            Broadcast();
        }

        // listen for user input on another thread
        new Thread(new UserListener()).start();

        // this thread dies here, but we have 2 threads running:
        // thread 1 is receiving UDP messages
        // thread 2 is receiving user input
    }

    // GOOD TO GO
    private void DoChangeCommand(HashMap<Integer, Double> updatedNeighbors) {

        for (int nPort : updatedNeighbors.keySet()){

            // ignore updates to non-neighbors
            if (!neighbors.containsKey(nPort)){
                continue;
            }

            double newLossRate = updatedNeighbors.get(nPort);

            // update our neighbor information
            Neighbor n = neighbors.get(nPort);
            n.UpdateLossRate(newLossRate);

            // inform neighbor of the new loss rate
            // NOTE: i am defining this message to be of the form 'CHANGE_<new-loss-rate>'
            n.SrNode.SendMessage(CHANGE_PREFIX + PREFIX_DELIM + n.LossRate);
        }

        // now all neighbors have been informed of new loss rate and have ACKed, so kickoff DV
        if (EnsureRoutingTableIsUpdated()) {
            DvPrinting.PrintRoutingTable(sourcePort, routingTable);
            Broadcast();
        }
    }

    // GOOD TO GO
    private void DoSendCommand(int destPort, int numPackets) {

        // ignore send to nodes we can't reach
        if (!routingTable.containsKey(destPort)) {
            System.out.println("Oops, cannot send to " + destPort);
            return;
        }

        // don't allow two simultaneous send commands
        if (currentSend != null) {
            System.out.println("Oops, you must wait for current send to finish.");
            return;
        }

        currentSend = new SendCommand(destPort, numPackets);

        // we're going to send to each neighbor
        for (int neighborPort : neighbors.keySet()) {
            currentSend.NeighborsToSend.add(neighborPort);
        }

        // send to one neighbor for now
        SendToNextNeighbor();

        // when we get a result back, that triggers sending to the next neighbor

    }

    // GOOD TO GO
    private void SendToNextNeighbor() {

        // if we've sent to everyone, we're done!
        if (currentSend.NeighborsToSend.isEmpty()) {
            currentSend = null;
            return;
        }

        // I am defining the send message as follows:
        // SEND_<source-node1>,<dest-node1>,<num_packets>
        String message = SEND_PREFIX + sourcePort + "," + currentSend.FinalDestPort + "," + currentSend.NumPackets;

        currentSend.NeighborPort = currentSend.NeighborsToSend.remove(0);
        currentSend.StartTime = Calendar.getInstance().getTimeInMillis();

        neighbors.get(currentSend.NeighborPort).SrNode.SendRandomPackets(currentSend.NumPackets, message);
    }

    // GOOD TO GO
    private void MessageDeliveryFromSR(int fromPort, String message, int numDroppedSinceLastDelivery){

        System.out.println("MESSAGE DELIVERY. " + fromPort + " says " + message);

        String[] msgParts = message.split(PREFIX_DELIM);
        String prefix = msgParts[0];
        String realMessage = msgParts[1];

        if (CHANGE_PREFIX.equals(prefix)) {
            HandleChangeFromNeighbor(fromPort, realMessage);
        }
        else if (BROADCAST_PREFIX.equals(prefix)) {
            HandleDvFromNeighbor(fromPort, realMessage);
        }
        else if (SEND_PREFIX.equals(prefix)) {
            HandleSendFromNeighbor(fromPort, realMessage, numDroppedSinceLastDelivery);
        }
        else if (END_OF_SEND_PREFIX.equals(prefix)) {
            HandleEndOfSendFromNeighbor(realMessage);
        }

        // ignore any other messages
    }

    // GOOD TO GO
    private void HandleChangeFromNeighbor(int neighborPort, String message) {
        double newLossRate;
        try {
            newLossRate = Double.parseDouble(message);
        }
        catch (Exception e) {
            return; // bad change message, just ignore it
        }

        // update the link
        neighbors.get(neighborPort).UpdateLossRate(newLossRate);

        // update the routing table and broadcast if necessary
        if (EnsureRoutingTableIsUpdated()) {
            DvPrinting.PrintRoutingTable(sourcePort, routingTable);
            Broadcast();
        }
    }

    // GOOD TO GO
    private void HandleDvFromNeighbor(int fromPort, String message) {

        // print that we received an update
        DvPrinting.PrintRcvMessage(sourcePort, fromPort);

        HashMap<Integer, RoutingTableEntry> neighborRoutingTable = new HashMap<Integer, RoutingTableEntry>();

        // parse the message into the neighbors routing table
        try {
            for (String entryString : message.split(" ")) {
                String[] entryParts = entryString.split(",");

                int toPort = Integer.parseInt(entryParts[0]);
                int neighborPort = Integer.parseInt(entryParts[1]);
                double weight = Double.parseDouble(entryParts[2]);

                neighborRoutingTable.put(toPort, new RoutingTableEntry(toPort, neighborPort, weight));
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
            DvPrinting.PrintRoutingTable(sourcePort, routingTable);
            Broadcast();
        }
        else if (!sentBroadcast) { // we need to broadcast at least once or DV initialization fails
            Broadcast();
        }
    }

    // GOOD TO GO
    private void HandleSendFromNeighbor(int fromPort, String message, int numDroppedSinceLastDelivery) {

        String[] parts = message.split(",");
        int originalSourcePort;
        int finalDestPort;
        int numPackets;

        try {
            originalSourcePort = Integer.parseInt(parts[0]);
            finalDestPort = Integer.parseInt(parts[1]);
            numPackets = Integer.parseInt(parts[2]);
        }
        catch (Exception e) {
            // received an improperly formatted message, which should never happen - just ignore it
            return;
        }

        if (!sendStatistics.containsKey(message)) {
            SendStat stat = new SendStat(message, 1, numDroppedSinceLastDelivery);
            sendStatistics.put(message, stat);
        }
        else {
            SendStat currentStat = sendStatistics.get(message);
            currentStat.NumReceived++;
            currentStat.NumDropped += numDroppedSinceLastDelivery;
        }

        SendStat stat = sendStatistics.get(message);

        // if we haven't received all of the packets, nothing to do yet
        if (stat.NumReceived != numPackets) {
            return;
        }

        // now that we've received all of the packets, we need to:
        // 1. print actual number packets received and actual loss rate
        // 2. check to see if we are the final destination
        // 3. if yes, send final timestamp back to original source port via routing table
        // 4. if no, start sending according to routing table

        // print out statistics
        SdPrinting.PrintFinishReceiving(fromPort, stat.GetTotalPackets(), stat.GetLossRate());

        if (finalDestPort == sourcePort) {
            // I am defining the end-of-send message as follows:
            // ENDSEND_<original-source-node>,<finish-time>
            String respMessage = END_OF_SEND_PREFIX + originalSourcePort + "," + Calendar.getInstance().getTimeInMillis();

            // send the final timestamp back to original source
            int neighborPort = routingTable.get(originalSourcePort).NeighborPort;
            neighbors.get(neighborPort).SrNode.SendMessage(respMessage);
        }
        else {
            // forward message using routing table
            int neighborPort = routingTable.get(finalDestPort).NeighborPort;
            neighbors.get(neighborPort).SrNode.SendRandomPackets(numPackets, message);
        }

    }

    // GOOD TO GO
    private void HandleEndOfSendFromNeighbor(String message) {
        String[] parts = message.split(",");

        int originalSourcePort;
        long finishTime;

        try {
            originalSourcePort = Integer.parseInt(parts[0]);
            finishTime = Long.parseLong(parts[1]);
        }
        catch (Exception e){
            // received an improperly formatted message, which should never happen - just ignore it
            return;
        }

        if (originalSourcePort == sourcePort) {

            // if we don't have an outstanding send, something went very wrong
            if (currentSend == null) {
                System.out.println("Oops, I was marked as the original sender but don't think I am.");
                return;
            }

            // sending is done, print the result
            long timeCost = finishTime - currentSend.StartTime;
            SdPrinting.PrintTimeCost(sourcePort, currentSend.NeighborPort, currentSend.FinalDestPort, timeCost);

            // send to the next neighbor
            SendToNextNeighbor();
        }
        else {
            // not for me, so just forward it along
            int neighborPort = routingTable.get(originalSourcePort).NeighborPort;
            neighbors.get(neighborPort).SrNode.SendMessage(message);
        }

    }

    // GOOD TO GO
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

    // GOOD TO GO
    private void Broadcast() {
        for (int neighborPort : neighbors.keySet()) {

            // I am defining the broadcast message as follows:
            // DV_<reachable-node1>,<next-node1>,<weight1> <reachable-node2>,<next-node2>,<weight2> ...
            String message = BROADCAST_PREFIX + PREFIX_DELIM;
            for (RoutingTableEntry entry : routingTable.values()) {
                // only tell neighbor we can get to places that don't go through them
                if (entry.NeighborPort != neighborPort && entry.ToPort != neighborPort){
                    message += entry.ToPort + "," + entry.NeighborPort + "," + entry.Weight + " ";
                }
            }

            // send it with selective repeat
            neighbors.get(neighborPort).SrNode.SendMessage(message);

            // print that we sent it
            DvPrinting.PrintSendMessage(sourcePort, neighborPort);
        }

        // mark that we have sent at least 1 broadcast
        sentBroadcast = true;
    }

    // GOOD TO GO
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
                for (int i = 0; i < msgParts.length; i += 2) {
                    int nPort = Integer.parseInt(msgParts[i]);
                    double nLossRate = Double.parseDouble(msgParts[i + 1]);

                    if (nPort <= 0 || nLossRate < 0) {
                        throw new IllegalArgumentException("Arguments outside valid range.");
                    }

                    updatedNeighbors.put(nPort, nLossRate);
                }
            }
            catch (Exception e) {
                UnrecognizedInput();
                return;
            }

            System.out.println("Doing change command");

            for (int key : updatedNeighbors.keySet()) {
                System.out.println("Neighbor " + key + " changed to " + updatedNeighbors.get(key));
            }

            DoChangeCommand(updatedNeighbors);
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

            DoSendCommand(destPort, numPackets);
        }

        private void UnrecognizedInput(){
            System.err.println("Oops, I don't recognize that command, try again.");
        }

    }

    // GOOD TO GO
    private class UdpListener implements Runnable {

        private int sourcePort;
        private DatagramSocket socket;

        public UdpListener(int sourcePort) throws SocketException {
            this.sourcePort = sourcePort;
            this.socket = new DatagramSocket(sourcePort);
        }

        @Override
        public void run() {
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

                // ignore messages from non-neighbors
                if (!neighbors.containsKey(fromPort)){
                    continue;
                }

                SRNode srNode = neighbors.get(fromPort).SrNode;

                // all packets start with packet number except special ACK packets
                if (msg.startsWith("ACK")) {
                    int packetNum;
                    try {
                        packetNum = Integer.parseInt(msg.split(",")[1]);
                    }
                    catch (Exception e) {
                        continue; // this should never happen, invalid ACK message
                    }
                    srNode.HandleReceivedAck(packetNum);
                }
                else {
                    Packet p = new Packet(msg, fromPort, sourcePort);
                    srNode.HandleReceived(p);
                }
            }
        }
    }

    // GOOD TO GO
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

    // this is NOT exactly the same as SRNode from part 1
    // it is similar, but tweaks have been made to fit needs of SDNode
    private class SRNode {

        public SRNode(int sourcePort, int destPort, int windowSize, int timeoutMs) throws SocketException {
            this.sourcePort = sourcePort;
            this.destPort = destPort;
            this.windowSize = windowSize;
            this.timeoutMs = timeoutMs;
            this.socket = new DatagramSocket(sourcePort);
        }

        private int sourcePort;     // send msgs from this port
        private int destPort;       // send msgs to this port
        private int windowSize;     // length of the ACK window
        private int timeoutMs;      // packet timeout
        private DatagramSocket socket;
        private int numDroppedSinceLastDeliver;

        private int sendNextSeqNum;
        private int sendWindowBase;
        private HashSet<Integer> ackedPackets = new HashSet<Integer>();
        private List<Integer> queuedPackets = new ArrayList<Integer>();
        private HashMap<Integer, Packet> sendPackets = new HashMap<Integer, Packet>();
        private HashMap<Integer, Long> inFlightPacketTimes = new HashMap<Integer, Long>();

        private int rcvWindowBase;
        private HashMap<Integer, Packet> rcvdPackets = new HashMap<Integer, Packet>();

        private class MessageDelivery implements Runnable {

            private int sourcePort;
            private String data;
            private int numDropped;

            public MessageDelivery(int sourcePort, String data) {
                this.sourcePort = sourcePort;
                this.data = data;
                this.numDropped = numDroppedSinceLastDeliver;
                numDroppedSinceLastDeliver = 0;
            }

            @Override
            public void run() {
                MessageDeliveryFromSR(sourcePort, data, numDropped);
            }
        }

        // GOOD TO GO
        public void HandleReceivedAck(int packetNum) {

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
                while (!queuedPackets.isEmpty() && queuedPackets.get(0) < sendWindowBase + windowSize) {
                    int nextPacketToSend = queuedPackets.remove(0);
                    SendOnePacket(sendPackets.get(nextPacketToSend));
                }
            }
            else {
                // just print ACK1, don't move window or send anything new
                SrSenderPrinting.PrintAck1(packetNum);
            }

        }

        // GOOD TO GO
        public void HandleReceived(Packet payload) {

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
                        Packet toDeliver = rcvdPackets.get(rcvWindowBase); // TODO should this be remove instead of get?
                        rcvWindowBase++;

                        // deliver data and keep processing
                        new Thread(new MessageDelivery(toDeliver.SourcePort, toDeliver.Data)).start();
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

        // GOOD TO GO
        public void SendRandomPackets(final int numPackets, String message) {

            List<Packet> packets = new ArrayList<Packet>();

            for (int i = 0; i < numPackets; i++) {
                Packet payload = new Packet(message, sendNextSeqNum++, sourcePort, destPort);
                packets.add(payload);
            }

            SendPacketsImpl(packets);

        }

        // GOOD TO GO
        public void SendMessage(final String message) {

            List<Packet> packets = new ArrayList<Packet>();

            Packet payload = new Packet(message, sendNextSeqNum++, sourcePort, destPort);
            packets.add(payload);

            SendPacketsImpl(packets);

        }

        // GOOD TO GO
        private void SendPacketsImpl(List<Packet> packets) {

            // print that we're starting
            SdPrinting.PrintStartSending(sourcePort);

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

                // wait 10 ms
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // if we get aborted, we're screwed
                }

                // if nothing is in flight, we're done!
                if (inFlightPacketTimes.isEmpty()) {
                    break;
                }

                for (Integer packetNum : inFlightPacketTimes.keySet()) {

                    // if the packet has been ACKed, no longer in flight
                    if (ackedPackets.contains(packetNum)) {
                        inFlightPacketTimes.remove(packetNum);
                        continue;
                    }

                    // check for timeout
                    if (inFlightPacketTimes.get(packetNum) + timeoutMs < Calendar.getInstance().getTimeInMillis()) {
                        SrSenderPrinting.PrintTimeout(packetNum);
                        SendOnePacket(sendPackets.get(packetNum));
                    }
                }

            }

            // print that we finished
            SdPrinting.PrintFinishSending(sourcePort);

            // now that we're done, no need to hold on to packets
            sendPackets.clear();
        }

        // GOOD TO GO
        private void SendOnePacket(final Packet payload) {
            inFlightPacketTimes.put(payload.Number, Calendar.getInstance().getTimeInMillis());
            UnreliableSend(payload.DestPort, payload.toString());
            SrSenderPrinting.PrintSendPacket(payload.Number, payload.Data);
        }

        // GOOD TO GO
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

    }

    // GOOD TO GO
    private static class SdPrinting {

        public static void PrintStartSending(int nodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " start " + nodePort;
            System.out.println(toPrint);
        }

        public static void PrintFinishSending(int nodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " finish " + nodePort;
            System.out.println(toPrint);
        }

        public static void PrintFinishReceiving(int nodePort, int totalNumberPacketsSent, double lossRate) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = timestamp + " " + nodePort + " " + totalNumberPacketsSent + " " + lossRate;
            System.out.println(toPrint);
        }

        public static void PrintTimeCost(int originalSourcePort, int nextPort, int finalDestPort, long timeCost) {
            String toPrint = originalSourcePort + " - " + nextPort + " -> " + finalDestPort + ": " + timeCost;
            System.out.println(toPrint);
        }

    }

    // GOOD TO GO
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

    // GOOD TO GO
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

    // GOOD TO GO
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
