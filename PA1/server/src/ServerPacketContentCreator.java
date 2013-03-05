import java.util.Collection;

public class ServerPacketContentCreator {

    public String AckLogin(boolean validName) {
        String sf = validName ? "S" : "F";
        return String.format("acklogin,%s", sf);
    }

    public String ListClients(Collection<ClientModel> clients) {
        StringBuilder builder = new StringBuilder("ackls");
        for (ClientModel client : clients) {
            builder.append(",");
            builder.append(client.Name);
            builder.append(",");
            String stateString = client.CurentState == ClientModel.State.Free ? "free" : client.CurentState == ClientModel.State.Busy ? "busy" : "decision";
            builder.append(stateString);
        }
        return builder.toString();
    }

    public String GameRequest(String chosenName) {
        return String.format("request,%s", chosenName);
    }

    public String AckChoose(String name, String status) {
        return String.format("ackchoose,%s,%s", name, status);
    }

    public String GameState(String state) {
        return String.format("play,%s", state);
    }

    public String AckPlay(MoveOutcome outcome) {
        String ot = outcome == MoveOutcome.Occupied ? "O" : "T";
        return String.format("ackplay,%s", ot);
    }

    public String GameResult(String outcome) {
        return String.format("result,%s", outcome);
    }

}

