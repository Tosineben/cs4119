import java.util.ArrayList;

public class SDNode {

    private static class Neighbor {

        public Neighbor(int port, double lossRate) {
            Port = port;
            LossRate = lossRate;
            Weight = 1 / (1 - lossRate);
        }

        public int Port;
        public double Weight;
        public double LossRate;
    }

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

        int port;                       // listen on this port
        ArrayList<Neighbor> neighbors;  // these are my neighbors
        boolean last;                   // if last, i complete graph, so start broadcast
        int windowSize = 10;            // length of the ACK window - fixed at 10
        int timeoutMs = 300;            // packet timeout - fixed at 300ms

        try {
            port = Integer.parseInt(args[0]);

            neighbors = new ArrayList<Neighbor>();

            // count args by two's, getting info for each neighbor
            for (int i = 1; i < args.length; i += 2) {
                int nPort = Integer.parseInt(args[i]);
                double nLossRate = Double.parseDouble(args[i + 1]);
                neighbors.add(new Neighbor(nPort, nLossRate));
            }

            last = "last".equals(args[args.length - 1]);
        }
        catch (Exception e) {
            System.err.println("Usage: SDNode <port-number> <neighbor1port> <neighbor1loss-rate> .... <neighboriport> <neighboriloss-rate> [last]?");
            return;
        }

        // accept string as user input, each character is sent as packet
    }

}
