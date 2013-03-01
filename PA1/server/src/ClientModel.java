public class ClientModel {

    public ClientModel(String name, String ip, int port) {
        Name = name;
        Port = port;
        IP = ip;
        State = ClientState.Free;
    }

    public String Name;
    public ClientState State;
    public int Port;
    public String IP;
}
