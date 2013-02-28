import java.io.IOException;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.System;
import java.net.DatagramSocket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReliableUDP {

    private UnreliableUDP unreliableUDP;

    public ReliableUDP() {
        unreliableUDP = new UnreliableUDP();
    }

    public void Send(String toIP, int toPort, String message) {
        try {
            final DatagramSocket socket = new DatagramSocket();

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
                unreliableUDP.Send(socket, toIP, toPort, message);

                // wait 100ms for an ack
                try {
                    receivedAck = future.get(100, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // swallow
                    System.out.println("didnt receive ack!"); // TODO: remove
                }
            }

            executor.shutdownNow();

        } catch (IOException e) {
            e.printStackTrace(); //TODO: remove
        }
    }

    public ReceivedMessage Receive(DatagramSocket receiverSocket) {
        try {
            ReceivedMessage msg = unreliableUDP.Receive(receiverSocket);
            msg.Message = msg.Message.trim();
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
        String msg = "ack," + packetId;
        DatagramSocket doWeNeedThis = new DatagramSocket(); //TODO: is this needed?
        unreliableUDP.Send(doWeNeedThis, fromIP, fromPort, msg);
    }

}
