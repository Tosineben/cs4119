public class SRNode {

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

        int sourcePort;     // send msgs from this port
        int destPort;       // receive msgs from this port
        int windowSize;     // length of the ACK window
        int timeoutMs;      // packet timeout
        double lossRate;    // packet loss rate

        try {
            sourcePort = Integer.parseInt(args[0]);
            destPort = Integer.parseInt(args[1]);
            windowSize = Integer.parseInt(args[2]);
            timeoutMs = Integer.parseInt(args[3]);
            lossRate = Double.parseDouble(args[4]);
        }
        catch (Exception e) {
            System.err.println("Usage: SRNode <source-port> <destination-port> <window-size> <time-out> <loss-rate>");
            return;
        }

        // accept string as user input, each character is sent as packet
    }

}
