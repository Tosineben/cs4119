import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UnreliableUDP {

    public void Send(DatagramSocket senderSocket, String toIP, int toPort, String message) throws IOException {
        PrintSend(toIP, toPort, message); //TODO: remove
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
        PrintReceive(fromIP, fromPort, msg); //TODO: remove
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
