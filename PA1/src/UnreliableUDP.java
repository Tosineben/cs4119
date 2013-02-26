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
        //TODO: remove debug printing
        PrintSend(receiverIP, receiverPort, message);
        InetAddress receiverAddress = InetAddress.getByName(receiverIP);
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, receiverPort);
        senderSocket.send(packet);
    }

    public ReceivedMessage Receive(DatagramSocket receiverSocket, byte[] buffer) throws IOException {
        DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receiverPacket);
        String fromIP = receiverPacket.getAddress().getHostAddress();
        int fromPort = receiverPacket.getPort();
        String msg = new String(buffer, 0, receiverPacket.getLength());
        //TODO: remove debug printing
        PrintReceive(fromIP, fromPort, msg);
        return new ReceivedMessage(fromIP, fromPort, msg);
    }

    private static void PrintReceive(String fromIP, int fromPort, String message) {
        System.out.println("[" + java.util.Calendar.getInstance().getTimeInMillis() +
            "] Receive from sender (IP: " + fromIP + ", Port: " + fromPort + "): " +  message);
    }

    private static void PrintSend(String toIP, int toPort, String message) {
        System.out.println("[" + java.util.Calendar.getInstance().getTimeInMillis() +
            "] Send to (IP: " + toIP + ", Port: " + toPort + "): " +  message);
    }

}
