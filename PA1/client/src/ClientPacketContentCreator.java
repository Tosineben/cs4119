public class ClientPacketContentCreator {

    private int packetId = 0;

    public String Login(String name, int port) {
        return String.format("login,%d,%s,%d", ++packetId, name, port);
    }

    public String QueryList(String name) {
        return String.format("list,%d,%s", ++packetId, name);
    }

    public String ChoosePlayer(String name, String name2) {
        return String.format("choose,%d,%s,%s", ++packetId, name, name2);
    }

    public String AckRequest(String name, String name1, boolean accept) {
        String ad = accept ? "A" : "D";
        return String.format("ackchoose,%d,%s,%s,%s", ++packetId, name, name1, ad);
    }

    public String PlayGame(String name, int number) {
        return String.format("play,%d,%s,%d", ++packetId, name, number);
    }

    public String Logout(String name) {
        return String.format("logout,%d,%s", ++packetId, name);
    }

}
