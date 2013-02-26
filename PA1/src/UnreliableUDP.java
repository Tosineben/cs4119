import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UnreliableUDP {

    public void Send(String receiverIP, int receiverPort, String message, int senderPort) throws IOException {
        DatagramSocket socket = new DatagramSocket(senderPort);
        Send(socket, receiverIP, receiverPort, message);
    }

    public void Send(String receiverIP, int receiverPort, String message) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        Send(socket, receiverIP, receiverPort, message);
    }

    private void Send(DatagramSocket senderSocket, String receiverIP, int receiverPort, String message) throws IOException {
        InetAddress receiverAddr = InetAddress.getByName(receiverIP);
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddr, receiverPort);
        senderSocket.send(packet);
    }

}
