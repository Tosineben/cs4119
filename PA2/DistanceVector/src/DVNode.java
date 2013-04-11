import java.util.ArrayList;

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

        int port;                       // listen on this port
        ArrayList<Neighbor> neighbors;  // these are my neighbors
        boolean last;                   // if last, i complete graph, so start broadcast

        try {
            port = Integer.parseInt(args[0]);

            neighbors = new ArrayList<Neighbor>();

            // count args by two's, getting info for each neighbor
            for (int i = 1; i < args.length; i += 2) {
                int neighborPort = Integer.parseInt(args[i]);
                double neighborWeight = Double.parseDouble(args[i + 1]);
                neighbors.add(new Neighbor(neighborPort, neighborWeight));
            }

            last = "last".equals(args[args.length - 1]);
        }
        catch (Exception e) {
            System.err.println("Usage: <port-number> <neighbor1port> <neighbor1weight> .... <neighboriport> <neighboriweight> [last]?");
            return;
        }



    }

}
