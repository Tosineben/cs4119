public class ClientPacketContentCreator {

    private int packetId;
    private int port;

    public String Login(String name) {
        port = 4118;
        packetId = 1;
        return String.format("login,{0},{1},{2}", packetId, name, port);
    }

    public String QueryList(String name) {
        return String.format("list,{0},{1}", packetId, name);
    }

    public String ChoosePlayer(String name, String name2) {
        return String.format("choose,{0},{1},{2}", packetId, name, name2);
    }

    public String AckRequest(String name, String name1, boolean accept) {
        return String.format("ackchoose,{0},{1},{2},{3}", packetId, name, name1, accept ? "A" : "D");
    }

    public String PlayGame(String name, int number) {
        return String.format("play,{0},{1},{2}", packetId, name, number);
    }

    public String Logout(String name) {
        return String.format("logout,{0},{1}", packetId, name);
    }

}
