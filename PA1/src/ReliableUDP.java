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
            ReceivedMessage msg = unreliableUDP.Receive(receiverSocket, buffer);

            String[] msgParts = msg.Message.split(",");

            int packetId = Integer.parseInt(msgParts[1]);

            AckReceive(msg.FromIP, msg.FromPort, packetId);

            return msg;
        }
        catch (Exception e) {
            return null;
        }
    }

    private void AckReceive(String fromIP, int fromPort, int packetId) throws IOException {
        String msg = String.format("ack,{0}", packetId);
        unreliableUDP.Send(fromIP, fromPort, msg);
    }

}
