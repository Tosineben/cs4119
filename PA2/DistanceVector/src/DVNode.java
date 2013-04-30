import java.io.*;
import java.net.*;
import java.util.*;

public class DVNode {

    public static void main(String[] args) {

        DVNode node;
        boolean last;

        try {
            int port = Integer.parseInt(args[0]);

            HashMap<Integer, Double> neighbors = new HashMap<Integer, Double>();

            // count args by two's, getting info for each neighbor
            for (int i = 1; i < args.length - 1; i += 2) {
                int nPort = Integer.parseInt(args[i]);
                double nWeight = Double.parseDouble(args[i + 1]);

                if (nPort <= 0 || nWeight < 0) {
                    throw new IllegalArgumentException("Arguments outside valid range.");
                }

                neighbors.put(nPort, nWeight);
            }

            node = new DVNode(port, neighbors);
            last = "last".equals(args[args.length - 1]);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Usage: <port-number> <neighbor1port> <neighbor1weight> .... <neighboriport> <neighboriweight> [last]?");
            return;
        }

        node.Initialize(last);

    }

    // *********************************************
    // ************** PRIVATE FIELDS ***************
    // *********************************************

    private final int sourcePort;
    private boolean sentBroadcast;
    private DatagramSocket socket;
    private HashMap<Integer, RoutingTableEntry> routingTable = new HashMap<Integer, RoutingTableEntry>();
    private HashMap<Integer, Neighbor> neighbors = new HashMap<Integer, Neighbor>();

    public DVNode(int port, HashMap<Integer, Double> neighbors) throws SocketException {
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

    // set up the DVNode
    public void Initialize(boolean isLast) {
        // set up the routing table
        EnsureRoutingTableIsUpdated();

        // print the routing table
        DvPrinting.PrintRoutingTable(sourcePort, routingTable);

        // start broadcast if we're last
        if (isLast) {
            Broadcast();
        }

        // listen for incoming updates
        ListenForUpdates();
    }

    // listen for incoming udp messages
    private void ListenForUpdates() {
        // receive updates forever
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

            HandleDvFromNeighbor(fromPort, msg);
        }
    }

    // handle a received udp message
    private void HandleDvFromNeighbor(int fromPort, String message) {

        // print that we received
        DvPrinting.PrintRcvMessage(sourcePort, fromPort);

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

            // if we have nothing to send, skip this neighbor
            if (message == null) {
                continue;
            }

            ReliableSend(neighborPort, message);
            DvPrinting.PrintSendMessage(sourcePort, neighborPort);
        }

        // mark that we have sent at least 1 broadcast
        sentBroadcast = true;
    }

    // we assume sending is reliable for DVNode
    private void ReliableSend(int toPort, String message) {
        try {
            // all communication is on the same machine, so use local host
            InetAddress receiverAddress = InetAddress.getLocalHost();
            byte[] buffer = message.getBytes();
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, receiverAddress, toPort);
            socket.send(datagram);
        }
        catch (Exception e) {
            // just swallow this, assignment says to assume UDP is reliable for this part
        }
    }

    // *********************************************
    // ************** HELPER CLASSES ***************
    // *********************************************

    // info we need to store about each neighbor to compute routing table
    private class Neighbor {
        public final int Port;
        public double Weight;
        public HashMap<Integer, RoutingTableEntry> Routes = new HashMap<Integer, RoutingTableEntry>();

        public Neighbor(int port, double weight) {
            Port = port;
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

    // defines the packet contents of each type of message
    private static class MessageCreator {

        public static String Broadcast(int neighborPort, HashMap<Integer, RoutingTableEntry> routingTable) {
            // I am defining the broadcast message as follows:
            // <reachable-node1>,<next-node1>,<weight1> <reachable-node2>,<next-node2>,<weight2> ...

            String message = "";
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
