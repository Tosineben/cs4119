public class ClientPacketContentCreator {

    private String name;
    private int packetId;
    private int port;

    public String Login(String clientName) {
        name = clientName;
        port = 4118;
        packetId = 1;
        return String.format("login,{0},{1},{2}", packetId, name, port);
    }

    public String QueryList() {
        return String.format("list,{0},{1}", packetId, name);
    }

    public String ChoosePlayer(String name2) {
        return String.format("choose,{0},{1},{2}", packetId, name, name2);
    }

    public String AcceptRequest(String name1) {
        return AckRequest(name1, true);
    }

    public String DenyRequest(String name1) {
        return AckRequest(name1, false);
    }

    private String AckRequest(String name1, boolean accept) {
        return String.format("ackchoose,{0},{1},{2},{3}", packetId, name, name1, accept ? "A" : "D");
    }

    public String PlayGame(int number) {
        return String.format("play,{0},{1},{2}", packetId, name, number);
    }

    public String Logout() {
        return String.format("logout,{0},{1}", packetId, name);
    }

}
