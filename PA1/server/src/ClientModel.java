public class ClientModel {

    public ClientModel(String name, int port) {
        Name = name;
        Port = port;
        State = ClientState.Free;
    }

    public String Name;
    public ClientState State;
    public int Port;
}
