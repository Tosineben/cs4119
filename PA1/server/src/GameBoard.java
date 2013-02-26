import Enums.*;

import java.lang.*;
import java.lang.String;

public class GameBoard {

    private int[][] board;
    private String playerToMove;
    private String player1;
    private String player2;

    public GameBoard(String player1, String player2) {
        this.board = new int[3][3];
        this.player1 = player1;
        this.player2 = player2;
        this.playerToMove = player1;
    }

    public MoveOutcome PlayMove(String player, int move) {
        // make sure move is in turn
        if (!player.equals(playerToMove)) {
            return MoveOutcome.OutOfTurn;
        }

        // make sure move is a valid space
        if (move < 1 || move > 9) {
            return MoveOutcome.Occupied;
        }

        // make sure the space is not occupied
        int row = (move-1)/3;
        int col = (move-1)-3*row;
        if (board[row][col] != 0) {
            return MoveOutcome.Occupied;
        }

        // move is ok, make it and switch player's turn
        if (player.equals(player1)) {
            board[row][col] = 1;
            playerToMove = player2;
        }
        else {
            board[row][col] = 2;
            playerToMove = player1;
        }
        return MoveOutcome.Ok;
    }

    public boolean IsDraw() {
        for (int[] row : board) {
            for (int space : row) {
                if (space == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public String CheckForWinner() {
        int winnerId = FindWinnerId();
        switch (winnerId){
            case 1:
                return player1;
            case 2:
                return player2;
            default:
                return null;
        }
    }

    private int FindWinnerId() {
        // check rows
        for (int i = 0; i < 3; i++) {
            if (board[i][0] > 0 && board[i][0] == board[i][1] && board[i][0] == board[i][2]) {
                return board[i][0];
            }
        }
        // check columns
        for (int i = 0; i < 3; i++) {
            if (board[0][i] > 0 && board[0][i] == board[1][i] && board[0][i] == board[2][i]) {
                return board[0][i];
            }
        }
        // check diagonals
        if (board[0][0] > 0 && board[0][0] == board[1][1] && board[0][0] == board[2][2]) {
            return board[0][0];
        }
        if (board[0][2] > 0 && board[0][2] == board[1][1] && board[0][2] == board[2][0]) {
            return board[0][2];
        }
        // no winner
        return 0;
    }

    public String GetOtherPlayerName(String myName) {
        return player1.equals(myName) ? player2 : player1;
    }

    @Override public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int[] row : board) {
            for (int space : row) {
                builder.append(space);
            }
        }
        return builder.toString();
    }

}
