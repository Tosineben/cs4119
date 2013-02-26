import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ReliableUDP {

    private UnreliableUDP unreliableUDP;

    public ReliableUDP() {
        unreliableUDP = new UnreliableUDP();
    }

    public void Send(String receiverIP, int receiverPort, String message, int senderPort) throws IOException {
        unreliableUDP.Send(receiverIP, receiverPort, message, senderPort);
    }

    public void Send(String receiverIP, int receiverPort, String message) throws IOException {
        unreliableUDP.Send(receiverIP, receiverPort, message);
    }

    public ReceivedMessage Receive(DatagramSocket receiverSocket, byte[] buffer) {
        try {
            DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(receiverPacket);

            String fromIP = receiverPacket.getAddress().getHostAddress();
            int fromPort = receiverPacket.getPort();

            String msg = new String(buffer, 0, receiverPacket.getLength());

            PrintReceive(fromIP, fromPort, msg);

            String[] msgParts = msg.split(",");

            int packetId = Integer.parseInt(msgParts[1]);

            AckReceive(fromIP, fromPort, packetId);

            return new ReceivedMessage(fromIP, fromPort, msg);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static void PrintReceive(String senderIP, int senderPort, String message) {
        System.out.println("[" + java.util.Calendar.getInstance().getTimeInMillis() +
                "] Receive from sender (IP: " + senderIP + ", Port: " +
                String.valueOf(senderPort) + "): " +  message);
    }

    private void AckReceive(String fromIP, int fromPort, int packetId) throws IOException {
        String msg = String.format("ack,{0}", packetId);
        unreliableUDP.Send(fromIP, fromPort, msg);
    }

    public class ReceivedMessage {

        private ReceivedMessage(String ip, int port, String message) {
            FromIP = ip;
            FromPort = port;
            Message = message;
        }

        public String FromIP;
        public int FromPort;
        public String Message;
    }

}
