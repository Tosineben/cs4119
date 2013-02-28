public class ReceivedMessage {

    public ReceivedMessage(String ip, int port, String message) {
        FromIP = ip;
        FromPort = port;
        Message = message;
    }

    public String FromIP;
    public int FromPort;
    public String Message;
}
