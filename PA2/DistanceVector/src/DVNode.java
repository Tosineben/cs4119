import java.io.*;
import java.net.*;
import java.util.*;

public class DVNode {

    // TODO not static
    private static class Neighbor {

        public Neighbor(int port, double weight) {
            Port = port;
            Weight = weight;
        }

        public int Port;      // port on which neighbor is listening
        public double Weight; // weight of link between this node and neighbor
    }

    public static void main(String[] args) {

        // TODO remove debug
        if (args.length == 0){
            args = new String[7];
            args[0] = "1111";
            args[1] = "2222";
            args[2] = "1.4";
            args[3] = "3333";
            args[4] = "1.5";
            args[5] = "4444";
            args[6] = "0.9";
        }

        HashMap<Integer, Double> neighborWeights = new HashMap<Integer, Double>();

        int port;                       // listen on this port
        ArrayList<Neighbor> neighbors;  // these are my neighbors
        boolean last;                   // if last, i complete graph, so start broadcast

        try {
            port = Integer.parseInt(args[0]);

            neighbors = new ArrayList<Neighbor>();

            // count args by two's, getting info for each neighbor
            for (int i = 1; i < args.length; i += 2) {
                int nPort = Integer.parseInt(args[i]);
                double nWeight = Double.parseDouble(args[i + 1]);
                neighbors.add(new Neighbor(nPort, nWeight));
                neighborWeights.put(nPort, nWeight);
            }

            last = "last".equals(args[args.length - 1]);
        }
        catch (Exception e) {
            System.err.println("Usage: <port-number> <neighbor1port> <neighbor1weight> .... <neighboriport> <neighboriweight> [last]?");
            return;
        }



    }

    private static void PrintSendMessage(int nodeFromListenPort, int nodeToListenPort) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " Message sent from Node " + nodeFromListenPort + " to Node " + nodeToListenPort;
        System.out.println(toPrint);
    }

    private static void PrintRcvMessage(int nodeReceivedListenPort, int nodeFromListPort) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " Message received at Node " + nodeReceivedListenPort + " from Node " + nodeFromListPort;
        System.out.println(toPrint);
    }

    // TODO figure out how to store DV table...
    private static void PrintRoutingTable(int nodePort, ArrayList<Neighbor> neighbors, HashMap<String, Neighbor> otherDudes) {
        long timestamp = Calendar.getInstance().getTimeInMillis();
        String toPrint = timestamp + " Node " + nodePort + " - Routing Table";
        for (Neighbor n : neighbors) {
            // TODO: round weights to 3 decimal places
            toPrint += "\nNode " + n.Port + " -> (" + n.Weight + " )";
        }
        for (String port : otherDudes.keySet()) {
            Neighbor next = otherDudes.get(port);
            toPrint += "\nNode " + port + " [next " + next.Port + "] -> (" + next.Weight + ")";
        }
        System.out.println(toPrint);
    }


    private static void UnreliableSend(DatagramSocket senderSocket, int toPort, String message) throws IOException {
        // all communication is on the same machine, so use local host
        InetAddress receiverAddress = InetAddress.getLocalHost();
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, toPort);
        senderSocket.send(packet);
    }

    public static String UnreliableReceive(DatagramSocket receiverSocket) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receiverPacket);
        String fromIP = receiverPacket.getAddress().getHostAddress();
        int fromPort = receiverPacket.getPort();
        // TODO return port
        String msg = new String(buffer, 0, receiverPacket.getLength()).trim();
        return msg;
    }

}
