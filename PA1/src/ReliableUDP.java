import java.io.IOException;
import java.net.DatagramSocket;

public class ReliableUDP {

    private UnreliableUDP unreliableUDP;

    public ReliableUDP() {
        unreliableUDP = new UnreliableUDP();
    }

    public void Send(String toIP, int toPort, String message) {
        try {
            DatagramSocket socket = new DatagramSocket();
            unreliableUDP.Send(socket, toIP, toPort, message);
        } catch (IOException e) {
            e.printStackTrace(); //TODO: remove
        }
    }

    public void Send(DatagramSocket senderSocket, String toIP, int toPort, String message) {
        try {
            unreliableUDP.Send(senderSocket, toIP, toPort, message);
        }
        catch (Exception e) {
            e.printStackTrace(); //TODO: remove this
        }
    }

    public ReceivedMessage Receive(DatagramSocket receiverSocket, byte[] buffer) {
        try {
            ReceivedMessage msg = unreliableUDP.Receive(receiverSocket, buffer);
            String[] msgParts = msg.Message.split(",");
            int packetId = Integer.parseInt(msgParts[1]);
            AckReceive(receiverSocket, msg.FromIP, msg.FromPort, packetId);
            return msg;
        }
        catch (Exception e) {
            e.printStackTrace(); //TODO: remove this
            return null;
        }
    }

    private void AckReceive(DatagramSocket socket, String fromIP, int fromPort, int packetId) throws IOException {
        String msg = String.format("ack,{0}", packetId);
        DatagramSocket doWeNeedThis = new DatagramSocket(); //TODO: is this needed?
        unreliableUDP.Send(doWeNeedThis, fromIP, fromPort, msg);
    }

}
