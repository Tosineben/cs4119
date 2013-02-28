import java.io.IOException;
import java.net.DatagramSocket;
import java.util.concurrent.*;

public class ReliableUDP {

    private UnreliableUDP unreliableUDP;

    public ReliableUDP() {
        unreliableUDP = new UnreliableUDP();
    }

    public void Send(final DatagramSocket socket, String toIP, int toPort, String message) {
        ReceivedMessage receivedAck = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<ReceivedMessage> future = executor.submit(new Callable<ReceivedMessage>() {
            @Override
            public ReceivedMessage call() throws Exception {
                return unreliableUDP.Receive(socket);
            }
        });

        while (receivedAck == null) {

            // send the message over unreliable channel
            try {
                unreliableUDP.Send(socket, toIP, toPort, message);
            }
            catch (IOException e) {
                // swallow it because we'll try again if no ACK
            }

            // wait 100ms for an ack
            try {
                receivedAck = future.get(400, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // swallow
                System.out.println("didnt receive ack!"); // TODO: remove
            }
        }

        executor.shutdownNow();
    }

    public ReceivedMessage Receive(DatagramSocket receiverSocket) {
        ReceivedMessage received;

        try {
            received = unreliableUDP.Receive(receiverSocket);
        }
        catch (IOException e) {
            return null; // swallow and return null
        }

        String[] msgParts = received.Message.split(",");

        // if we can parse a packetId, ACK it
        if (msgParts.length >= 2) {
            Integer packetId = Utility.TryParseInt(msgParts[1]);
            if (packetId != null) {
                AckReceive(receiverSocket, received.FromIP, received.FromPort, packetId);
            }
        }

        return received;
    }

    private void AckReceive(DatagramSocket socket, String fromIP, int fromPort, int packetId) {
        String message = String.format("ack,%d", packetId);
        try {
            unreliableUDP.Send(socket, fromIP, fromPort, message);
        }
        catch (IOException e) {
            // swallow it because assignment says to assume sending ACK's will work
        }
    }

}
