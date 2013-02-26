import Enums.*;

import java.util.Collection;

public class ServerPacketContentCreator {

    public String AckLogin(boolean validName) {
        String sf = validName ? "S" : "F";
        return String.format("acklogin,{0}", sf);
    }

    public String ListClients(Collection<ClientModel> clients) {
        StringBuilder builder = new StringBuilder("ackls");
        for (ClientModel client : clients) {
            builder.append(",");
            builder.append(client.Name);
            builder.append(",");
            String stateString = client.State == ClientState.Free ? "free" : client.State == ClientState.Busy ? "busy" : "decision";
            builder.append(stateString);
        }
        return builder.toString();
    }

    public String GameRequest(String chosenName) {
        return String.format("request,{0}", chosenName);
    }

    public String AckChoose(String name, RequestStatus status) {
        String adf = status == RequestStatus.Accepted ? "A" : status == RequestStatus.Denied ? "D" : "F";
        return String.format("ackchoose,{0},{1}", name, adf);
    }

    public String GameState(String state) {
        return String.format("play,{0}", state);
    }

    public String AckPlay(MoveOutcome outcome) {
        String ot = outcome == MoveOutcome.Occupied ? "O" : "T";
        return String.format("ackplay,{0}", ot);
    }

    public String GameResult(GameOutcome outcome) {
        String wld = outcome == GameOutcome.Win ? "W" : outcome == GameOutcome.Loss ? "L" : "D";
        return String.format("result,{0}", wld);
    }

}

