public class ClientModel {

    public ClientModel(String name, String ip, int port) {
        Name = name;
        Port = port;
        IP = ip;
        CurentState = State.Free;
    }

    public String Name;
    public State CurentState;
    public int Port;
    public String IP;

    public static enum State {
        Free,
        Busy,
        Decision,
    }
}
