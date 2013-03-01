import java.io.IOException;
import java.net.*;

public class UnreliableUDP {

    public void Send(DatagramSocket senderSocket, String toIP, int toPort, String message) throws IOException {
        InetAddress receiverAddress = InetAddress.getByName(toIP);
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, toPort);
        senderSocket.send(packet);
    }

    public ReceivedMessage Receive(DatagramSocket receiverSocket) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receiverPacket);
        String fromIP = receiverPacket.getAddress().getHostAddress();
        int fromPort = receiverPacket.getPort();
        String msg = new String(buffer, 0, receiverPacket.getLength()).trim();
        return new ReceivedMessage(fromIP, fromPort, msg);
    }

}
