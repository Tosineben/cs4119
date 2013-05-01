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

                if (nPort <= 0 || nLossRate < 0 || nLossRate >= 1) {
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

    // *********************************************
    // ************** PRIVATE FIELDS ***************
    // *********************************************

    // port number of this node and associated socket
    private final int sourcePort;
    private DatagramSocket socket;

    // marker for if we did at least 1 DV broadcast
    private boolean sentBroadcast;

    // routing table for this node
    private HashMap<Integer, RoutingTableEntry> routingTable = new HashMap<Integer, RoutingTableEntry>();

    // neighbor information
    private HashMap<Integer, Neighbor> neighbors = new HashMap<Integer, Neighbor>();

    // current "send" command and associated statistics
    private SendCommand currentSend;
    private HashMap<String, SendStat> sendStatistics = new HashMap<String, SendStat>();

    // lock needed because we receive udp messages on a different thread
    private static final Object udpLock = new Object();

    public SDNode(int port, HashMap<Integer, Double> neighbors) throws SocketException {
        this.sourcePort = port;

        this.socket = new DatagramSocket(port);

        for(Map.Entry<Integer, Double> neighbor : neighbors.entrySet()) {
            Neighbor n = new Neighbor(neighbor.getKey(), neighbor.getValue());
            this.neighbors.put(n.Port, n);
        }
    }

    // *********************************************
    // ***************** METHODS *******************
    // *********************************************

    // set up the SDNode
    public void Initialize(boolean isLast) {

        // set up the routing table
        EnsureRoutingTableIsUpdated();

        // print the routing table
        DvPrinting.PrintRoutingTable(sourcePort, routingTable);

        // listen for incoming udp on another thread, do this before broadcast
        new Thread(new UdpListener(socket)).start();

        // start broadcast if we're last
        if (isLast) {
            Broadcast();
        }

        // listen for user input on another thread
        new Thread(new UserListener()).start();
    }

    // do the "change" command
    // tell neighbors about the update loss rates,
    // and once everyone ACKed, update our routing table
    private void DoChangeCommand(HashMap<Integer, Double> updatedNeighbors) {

        for (int nPort : updatedNeighbors.keySet()){

            // ignore updates to non-neighbors
            if (!neighbors.containsKey(nPort)){
                continue;
            }

            double newLossRate = updatedNeighbors.get(nPort);

            Neighbor n = neighbors.get(nPort);

            // if loss rate didn't change, don't send update
            if (n.LossRate == newLossRate) {
                continue;
            }

            // update our neighbor information
            n.UpdateLossRate(newLossRate);

            // inform our neighbor of the change
            String message = MessageCreator.Change(n.LossRate);
            n.SrNode.SendMessage(message, false);
        }

        // now all neighbors have been informed of new loss rate and have ACKed, so kickoff DV
        if (EnsureRoutingTableIsUpdated()) {
            DvPrinting.PrintRoutingTable(sourcePort, routingTable);
            Broadcast();
        }
    }

    // do the "send" command
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

        // send to first neighbor
        // sending to other neighbors gets triggered when this finishes
        SendToNextNeighbor();
    }

    // do the "send" command for the next neighbor
    // this gets called on send start and when each neighbor
    // send completes, until we've checked all neighbors
    private void SendToNextNeighbor() {

        // if we've sent to everyone, we're done!
        if (currentSend.NeighborsToSend.isEmpty()) {
            currentSend = null;
            return;
        }

        String message = MessageCreator.Send(sourcePort, currentSend.FinalDestPort, currentSend.NumPackets);

        currentSend.NeighborPort = currentSend.NeighborsToSend.remove(0); // get next neighbor
        currentSend.StartTime = Calendar.getInstance().getTimeInMillis(); // mark start time

        neighbors.get(currentSend.NeighborPort).SrNode.SendRandomPackets(currentSend.NumPackets, message);
    }

    // this gets hit when selective repeat (SRNode) has a set of in-order
    // packets ready to be delivered
    private void MessageDeliveryFromSR(int fromPort, String message, int numReceivedSinceLastDeliver){

        String[] msgParts = message.split(MessageCreator.PREFIX_DELIM);
        String prefix = msgParts[0];
        String realMessage = msgParts[1];

        if (MessageCreator.CHANGE_PREFIX.equals(prefix)) {
            HandleChangeFromNeighbor(fromPort, realMessage);
        }
        else if (MessageCreator.BROADCAST_PREFIX.equals(prefix)) {
            HandleDvFromNeighbor(fromPort, realMessage);
        }
        else if (MessageCreator.SEND_PREFIX.equals(prefix)) {
            HandleSendFromNeighbor(fromPort, realMessage, numReceivedSinceLastDeliver);
        }
        else if (MessageCreator.END_OF_SEND_PREFIX.equals(prefix)) {
            HandleEndOfSendFromNeighbor(realMessage);
        }

        // ignore any other messages
    }

    // handle a change update from a neighbor (our link loss rate changed)
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

    // handle a distance vector update from a neighbor
    private void HandleDvFromNeighbor(int fromPort, String message) {

        // print that we received an update
        DvPrinting.PrintRcvMessage(sourcePort, fromPort);

        HashMap<Integer, RoutingTableEntry> neighborRoutingTable = new HashMap<Integer, RoutingTableEntry>();

        // parse the message into the neighbors routing table
        try {
            for (String entryString : message.split(" ")) {
                if (entryString.isEmpty()) {
                    continue;
                }

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

    // handle a send update from neighbor
    // once we've received all packets, either forward to next neighbor
    // or print statistics and send pack "timestamp packet"
    private void HandleSendFromNeighbor(int fromPort, String message, int numReceivedSinceLastDeliver) {

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
            SendStat stat = new SendStat(message, 1, numReceivedSinceLastDeliver);
            sendStatistics.put(message, stat);
        }
        else {
            SendStat currentStat = sendStatistics.get(message);
            currentStat.NumValidReceived++;
            currentStat.TotalNumReceived += numReceivedSinceLastDeliver;
        }

        SendStat stat = sendStatistics.get(message);

        // if we haven't received all of the packets, nothing to do yet
        if (stat.NumValidReceived != numPackets) {
            return;
        }

        // now that we've received all of the packets, we need to:
        // 1. print actual number packets received and actual loss rate
        // 2. check to see if we are the final destination
        // 3. if yes, send final timestamp back to original source port via routing table
        // 4. if no, start sending according to routing table

        // print out statistics then clear them
        SdPrinting.PrintFinishReceiving(fromPort, stat.TotalNumReceived, stat.GetLossRate());
        sendStatistics.remove(message);

        if (finalDestPort == sourcePort) {
            // send the final timestamp back to original source using routing table
            String msgToSend = MessageCreator.EndOfSend(originalSourcePort);
            int neighborPort = routingTable.get(originalSourcePort).NeighborPort;
            neighbors.get(neighborPort).SrNode.SendMessage(msgToSend, true);
        }
        else {
            // forward message using routing table
            String msgToSend = MessageCreator.Send(message);
            int neighborPort = routingTable.get(finalDestPort).NeighborPort;
            neighbors.get(neighborPort).SrNode.SendRandomPackets(numPackets, msgToSend);
        }

    }

    // handle a "timestamp packet" from neighbor
    // either forward to next neighbor or print statistics
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
            String msgToSend = MessageCreator.EndOfSend(message);
            int neighborPort = routingTable.get(originalSourcePort).NeighborPort;
            neighbors.get(neighborPort).SrNode.SendMessage(msgToSend, true);
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

    // Distance Vector broadcast of our routing table to all neighbors
    private void Broadcast() {
        for (int neighborPort : neighbors.keySet()) {

            String message = MessageCreator.Broadcast(neighborPort, routingTable);

            // if we haven't broadcast at least once,
            // AND we have nothing to send this neighbor, send a blank message just to
            // trigger the initial DV flood
            if (message == null && !sentBroadcast) {
                message = MessageCreator.BROADCAST_PREFIX + MessageCreator.PREFIX_DELIM + " _";
            }

            // if we have nothing to send, skip this neighbor
            if (message == null) {
                continue;
            }

            // send it with selective repeat
            neighbors.get(neighborPort).SrNode.SendMessage(message, false);

            // print that we sent it
            DvPrinting.PrintSendMessage(sourcePort, neighborPort);
        }

        // mark that we have sent at least 1 broadcast
        sentBroadcast = true;
    }

    // *********************************************
    // ************** HELPER CLASSES ***************
    // *********************************************

    // info we need to store about each neighbor to compute routing table
    // also note that each neighbor has an SRNode, because we keep track
    // of selective repeat on a per neighbor basis
    private class Neighbor {
        public final int Port;
        public double Weight;
        public double LossRate;
        public HashMap<Integer, RoutingTableEntry> Routes = new HashMap<Integer, RoutingTableEntry>();
        public SRNode SrNode;

        public Neighbor(int port, double lossRate) {
            Port = port;

            // defaults from assignment description: windowSize = 10, timeout = 300ms
            SrNode = new SRNode(socket, port, 10, 300);

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

        public SendStat(String message, int numValidReceived, int totalNumReceived) {
            Message = message;
            NumValidReceived = numValidReceived;
            TotalNumReceived = totalNumReceived;
        }

        public String Message;
        public int NumValidReceived;
        public int TotalNumReceived;

        public double GetLossRate() {
            int numDropped = TotalNumReceived - NumValidReceived;
            double lossRate = (double)numDropped / TotalNumReceived;
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

    // thread that listens for user input and delegates
    // different commands to the SDNode
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

                    if (nPort <= 0 || nLossRate < 0 || nLossRate >= 1) {
                        throw new IllegalArgumentException("Arguments outside valid range.");
                    }

                    updatedNeighbors.put(nPort, nLossRate);
                }
            }
            catch (Exception e) {
                UnrecognizedInput();
                return;
            }

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

    // thread that listens for udp messages, simulates dropped
    // packets, and informs appropriate SRNode on successful receive
    private class UdpListener implements Runnable {

        private DatagramSocket socket;
        public UdpListener(DatagramSocket socket) {
            this.socket = socket;
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

                Neighbor n = neighbors.get(fromPort);

                // if it's not an ACK, mark that we received from neighbor
                if (!msg.startsWith("ACK")) {
                    n.SrNode.numReceivedSinceLastDeliver++;
                }

                // simulates "dropped" packets based on neighbor loss rate
                if (new Random().nextDouble() < n.LossRate) {
                    continue;
                }

                // tell SrNode that we either received an ACK or a message
                if (msg.startsWith("ACK")) {
                    int packetNum;
                    try {
                        packetNum = Integer.parseInt(msg.split(",")[1]);
                    }
                    catch (Exception e) {
                        continue; // this should never happen, invalid ACK message
                    }
                    n.SrNode.HandleReceivedAck(packetNum);
                }
                else {
                    Packet p = new Packet(msg, fromPort, sourcePort);
                    n.SrNode.HandleReceived(p);
                }
            }
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

    // this is NOT exactly the same as SRNode from part 1
    // it is similar, but tweaks have been made to fit needs of SDNode
    private class SRNode {

        public SRNode(DatagramSocket socket, int destPort, int windowSize, int timeoutMs) {
            this.destPort = destPort;
            this.windowSize = windowSize;
            this.timeoutMs = timeoutMs;
            this.socket = socket;
        }

        // private fields used by Selective Repeat
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

        // extra info for "send" command
        // when we deliver messages, include the total number of packets received
        private int numReceivedSinceLastDeliver;

        // when we receive an ACK, possibly shift window and possibly send
        // more packets if any are waiting to be sent
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

                // send all pending packets that are inside the new window
                while (!queuedPackets.isEmpty() && queuedPackets.get(0) < sendWindowBase + windowSize) {
                    int nextPacketToSend = queuedPackets.remove(0);
                    SendOnePacket(sendPackets.get(nextPacketToSend));
                }
            }

            // note that SDNode does not print any SR info (ACK1, ACK2)

        }

        // when we receive a packet, possibly shift window and
        // deliver data to next layer up, and always send an ACK
        public void HandleReceived(Packet payload) {

            if (payload.Number >= rcvWindowBase + windowSize) {
                // this should never happen because we can assume sender/receiver windows are the same
                // see this post: https://piazza.com/class#spring2013/csee4119/152
                return;
            }

            // send an ACK no matter what
            UnreliableSend(payload.SourcePort, "ACK," + payload.Number);

            // if the packet is before our window or we've received it, discard it
            if (payload.Number < rcvWindowBase || rcvdPackets.containsKey(payload.Number)) {
                return;
            }

            // mark the packet received
            rcvdPackets.put(payload.Number, payload);

            // if this is the first packet in our window, shift window and deliver data (in theory)
            if (payload.Number == rcvWindowBase) {

                // deliver data and shift the window up to the next packet we need
                List<Packet> toDeliver = new ArrayList<Packet>();
                while (rcvdPackets.containsKey(rcvWindowBase)) {
                    Packet p = rcvdPackets.get(rcvWindowBase);
                    toDeliver.add(p);
                    rcvWindowBase++;
                }

                // deliver data and keep processing
                new Thread(new MessageDelivery(toDeliver)).start();
            }

            // note that SDNode does not print any SR info (Discard, Receive1, Receive2)
        }

        // send a bunch of random packets
        public void SendRandomPackets(final int numPackets, String message) {
            List<Packet> packets = new ArrayList<Packet>();
            for (int i = 0; i < numPackets; i++) {
                Packet payload = new Packet(message, sendNextSeqNum++, sourcePort, destPort);
                packets.add(payload);
            }
            SendPacketsImpl(packets, true);
        }

        // send a "normal" message
        public void SendMessage(final String message, boolean shouldPrintStartFinish) {
            List<Packet> packets = new ArrayList<Packet>();
            Packet payload = new Packet(message, sendNextSeqNum++, sourcePort, destPort);
            packets.add(payload);
            SendPacketsImpl(packets, shouldPrintStartFinish);
        }

        // ensures that a set of packets is sent successfully
        // sends them once, then monitors for timeouts until
        // all packets are ACKed
        private void SendPacketsImpl(List<Packet> packets, boolean shouldPrintStartFinish) {

            // if we're printing, print finish
            if (shouldPrintStartFinish) {
                SdPrinting.PrintStartSending(sourcePort);
            }

            // lock here so that we only handle on "send" command at a time
            synchronized (udpLock) {

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

                // at this point, all packets have been ACKed

                if (shouldPrintStartFinish) {
                    SdPrinting.PrintFinishSending(sourcePort);
                }
            }
        }

        // send a packet unreliably and update it's timestamp
        private void SendOnePacket(final Packet payload) {
            inFlightPacketTimes.put(payload.Number, Calendar.getInstance().getTimeInMillis());
            UnreliableSend(payload.DestPort, payload.toString());
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

        // thread for delivering messages to upper layer
        // the basic idea is that we want to continue to receive ACKs
        // even when Selective Repeat determines it has a set of
        // in-order packets to deliver. this is not long-living, so
        // we have at most 3 threads open at once (one listening for user,
        // one listening for udp, and this one which processes messages)
        private class MessageDelivery implements Runnable {

            private int numReceived;
            private List<Packet> toDeliver;

            public MessageDelivery(List<Packet> toDeliver) {
                this.toDeliver = toDeliver;
                this.numReceived = numReceivedSinceLastDeliver;
                numReceivedSinceLastDeliver = 0;
            }

            @Override
            public void run() {
                // lock because we cannot deliver a message if we're still sending
                synchronized (udpLock) {
                    for (Packet p : toDeliver) {
                        MessageDeliveryFromSR(p.SourcePort, p.Data, numReceived);
                        numReceived = 0;
                    }
                }
            }
        }
    }

    // defines the packet contents of each type of message
    private static class MessageCreator {

        // helpers for defining packet payloads
        public static final String PREFIX_DELIM = "_";
        public static final String CHANGE_PREFIX = "CHANGE";
        public static final String BROADCAST_PREFIX = "DV";
        public static final String SEND_PREFIX = "SEND";
        public static final String END_OF_SEND_PREFIX = "END";

        public static String EndOfSend(int originalSourcePort) {
            // I am defining the end-of-send message as follows:
            // END_<original-source-node>,<finish-time>

            return END_OF_SEND_PREFIX + PREFIX_DELIM + originalSourcePort + "," + Calendar.getInstance().getTimeInMillis();
        }

        public static String EndOfSend(String receivedMessage) {
            // this happens when forwarding a message back to original source
            return END_OF_SEND_PREFIX + PREFIX_DELIM + receivedMessage;
        }

        public static String Send(int sourcePort, int finalDestPort, int numPackets) {
            // I am defining the send message as follows:
            // SEND_<source-node>,<dest-node>,<num_packets>

            return SEND_PREFIX + PREFIX_DELIM + sourcePort + "," + finalDestPort + "," + numPackets;
        }

        public static String Send(String receivedMessage) {
            // this happens when forwarding a message to the final destination
            return SEND_PREFIX + PREFIX_DELIM + receivedMessage;
        }

        public static String Change(double newLossRate) {
            // I am defining the change message as follows:
            // CHANGE_<new-loss-rate>

            return CHANGE_PREFIX + PREFIX_DELIM + newLossRate;
        }

        public static String Broadcast(int neighborPort, HashMap<Integer, RoutingTableEntry> routingTable) {
            // I am defining the broadcast message as follows:
            // DV_<reachable-node1>,<next-node1>,<weight1> <reachable-node2>,<next-node2>,<weight2> ...

            String message = BROADCAST_PREFIX + PREFIX_DELIM;
            for (RoutingTableEntry entry : routingTable.values()) {
                // only tell neighbor we can get to places that don't go through them
                if (entry.NeighborPort != neighborPort && entry.ToPort != neighborPort){
                    message += entry.ToPort + "," + entry.NeighborPort + "," + entry.Weight + " ";
                }
            }

            // if we have nothing to send to this neighbor, don't send
            if (!message.endsWith(" ")) {
                return null;
            }
            else {
                return message.trim();
            }
        }

    }

    // defines what we print for the SD component
    private static class SdPrinting {

        public static void PrintStartSending(int nodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] start " + nodePort;
            System.out.println(toPrint);
        }

        public static void PrintFinishSending(int nodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] finish " + nodePort;
            System.out.println(toPrint);
        }

        public static void PrintFinishReceiving(int nodePort, int totalNumberPacketsSent, double lossRate) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] " + nodePort + " " + totalNumberPacketsSent + " " + lossRate;
            System.out.println(toPrint);
        }

        public static void PrintTimeCost(int originalSourcePort, int nextPort, int finalDestPort, long timeCost) {
            String toPrint = originalSourcePort + " - " + nextPort + " -> " + finalDestPort + ": " + timeCost;
            System.out.println(toPrint);
        }

    }

    // defines what we print for the DV component
    private static class DvPrinting {

        public static void PrintSendMessage(int sourceNodePort, int destNodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] Message sent from Node " + sourceNodePort + " to Node " + destNodePort;
            System.out.println(toPrint);
        }

        public static void PrintRcvMessage(int destNodePort, int sourceNodePort) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] Message received at Node " + destNodePort + " from Node " + sourceNodePort;
            System.out.println(toPrint);
        }

        public static void PrintRoutingTable(int nodePort, HashMap<Integer, RoutingTableEntry> routingTable) {
            long timestamp = Calendar.getInstance().getTimeInMillis();
            String toPrint = "[" + timestamp + "] Node " + nodePort + " - Routing Table";
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

}
