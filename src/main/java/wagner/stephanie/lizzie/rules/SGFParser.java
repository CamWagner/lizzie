package wagner.stephanie.lizzie.rules;

import java.util.ArrayList;
import wagner.stephanie.lizzie.Lizzie;

import java.io.*;

public class SGFParser {
    public static boolean load(String filename) throws IOException {
        // Clear the board
        Lizzie.board.clear();

        File file = new File(filename);
        if (!file.exists() || !file.canRead()) {
            return false;
        }

        FileInputStream fp = new FileInputStream(file);
        InputStreamReader reader = new InputStreamReader(fp);
        StringBuilder builder = new StringBuilder();
        while (reader.ready()) {
            builder.append((char) reader.read());
        }
        reader.close();
        fp.close();

        String value = builder.toString();
        if (value.isEmpty()) {
            return false;
        }
        return parse(value);
    }

    public static int[] convertSgfPosToCoord(String pos) {
        if (pos.equals("tt") || pos.isEmpty())
            return null;
        int[] ret = new int[2];
        ret[0] = (int) pos.charAt(0) - 'a';
        ret[1] = (int) pos.charAt(1) - 'a';
        return ret;
    }

    public static String convertCoordToSgfPos(int[] pos) {
        if (pos == null) {
            return "";
        }
        char x = (char) (pos[0] + 'a');
        char y = (char) (pos[1] + 'a');
        return String.format("%c%c", x, y);
    }

    private static boolean parse(String value) {
        value = value.trim();
        if (value.charAt(0) != '(') {
            return false;
        } else if (value.charAt(value.length() - 1) != ')') {
            return false;
        }
        int subTreeDepth = 0;
        boolean inTag = false, isMultiGo = false;
        String tag = null;
        StringBuilder tagBuilder = new StringBuilder();
        StringBuilder tagContentBuilder = new StringBuilder();
        // MultiGo 's branch: (Main Branch (Main Branch) (Branch) )
        // Other 's branch: (Main Branch (Branch) Main Branch)
        if (value.charAt(value.length() - 2) == ')') {
            isMultiGo = true;
        }

        String whitePlayer = "";
        String blackPlayer = "";

        for (byte b : value.getBytes()) {
            // Check unicode charactors (UTF-8)
            char c = (char) b;
            if (((int)b & 0x80) != 0) {
                continue;
            }
            switch (c) {
            case '(':
                if (!inTag) {
                    subTreeDepth += 1;
                }
                break;
            case ')':
                if (!inTag) {
                    subTreeDepth -= 1;
                    if (isMultiGo) {
                        return true;
                    }
                }
                break;
            case '[':
                if (subTreeDepth > 1 && !isMultiGo) {
                    break;
                }
                inTag = true;
                String tagTemp = tagBuilder.toString();
                if (!tagTemp.isEmpty()) {
                    tag = tagTemp;
                }
                tagContentBuilder = new StringBuilder();
                break;
            case ']':
                if (subTreeDepth > 1 && !isMultiGo) {
                    break;
                }
                inTag = false;
                tagBuilder = new StringBuilder();
                String tagContent = tagContentBuilder.toString();
                // We got tag, we can parse this tag now.
                if (tag.equals("B")) {
                    int[] move = convertSgfPosToCoord(tagContent);
                    if (move == null) {
                        Lizzie.board.pass(Stone.BLACK);
                    } else {
                        Lizzie.board.place(move[0], move[1], Stone.BLACK);
                    }
                } else if (tag.equals("W")) {
                    int[] move = convertSgfPosToCoord(tagContent);
                    if (move == null) {
                        Lizzie.board.pass(Stone.WHITE);
                    } else {
                        Lizzie.board.place(move[0], move[1], Stone.WHITE);
                    }
                } else if (tag.equals("AB")) {
                    int[] move = convertSgfPosToCoord(tagContent);
                    if (move == null) {
                        Lizzie.board.pass(Stone.BLACK);
                    } else {
                        Lizzie.board.place(move[0], move[1], Stone.BLACK);
                    }
                    Lizzie.board.flatten();
                } else if (tag.equals("AW")) {
                    int[] move = convertSgfPosToCoord(tagContent);
                    if (move == null) {
                        Lizzie.board.pass(Stone.WHITE);
                    } else {
                        Lizzie.board.place(move[0], move[1], Stone.WHITE);
                    }
                    Lizzie.board.flatten();
                } else if (tag.equals("PW")) {
                    whitePlayer = tagContent;
                } else if (tag.equals("PB")) {
                    blackPlayer = tagContent;
                }
                break;
            case ';':
                break;
            default:
                if (subTreeDepth > 1 && !isMultiGo) {
                    break;
                }
                if (inTag) {
                    tagContentBuilder.append(c);
                } else {
                    if (c != '\n' && c != '\r' && c != '\t' && c != ' ') {
                        tagBuilder.append(c);
                    }
                }
            }
        }

        Lizzie.frame.setPlayers(whitePlayer, blackPlayer);

        // Rewind to game start
        while (Lizzie.board.previousMove());

        return true;
    }

    public static boolean save(String filename) throws IOException {
        FileOutputStream fp = new FileOutputStream(filename);
        OutputStreamWriter writer = new OutputStreamWriter(fp);
        // add SGF header
        StringBuilder builder = new StringBuilder(String.format("(;KM[7.5]AP[Lizzie: %s]", Lizzie.lizzieVersion));

        BoardHistoryList history = Lizzie.board.getHistory();
        while (history.previous() != null);
        BoardData data;

        data = history.getData();

        // For handicap
        ArrayList<int[]> abList = new ArrayList<int[]>();
        ArrayList<int[]> awList = new ArrayList<int[]>();

        for (int i = 0; i < Board.BOARD_SIZE; i++) {
            for (int j = 0; j < Board.BOARD_SIZE; j++) {
                switch (data.stones[Board.getIndex(i, j)]) {
                case BLACK:
                    abList.add(new int[]{i, j});
                    break;
                case WHITE:
                    awList.add(new int[]{i, j});
                    break;
                default:
                    break;
                }
            }
        }

        if (!abList.isEmpty()) {
            builder.append(";AB");
            for (int i = 0; i < abList.size(); i++) {
                builder.append(String.format("[%s]", convertCoordToSgfPos(abList.get(i))));
            }
        }

        if (!awList.isEmpty()) {
            builder.append(";AW");
            for (int i = 0; i < awList.size(); i++) {
                builder.append(String.format("[%s]", convertCoordToSgfPos(awList.get(i))));
            }
        }

        while ((data = history.next()) != null) {
            StringBuilder tag = new StringBuilder();
            tag.append(";");
            switch (data.lastMoveColor) {
            case BLACK:
                tag.append("B");
                break;
            case WHITE:
                tag.append("W");
                break;
            default:
                break;
            }

            tag.append(String.format("[%s]", convertCoordToSgfPos(data.lastMove)));
        }

        // close file
        builder.append(')');
        writer.append(builder.toString());
        writer.close();
        fp.close();
        return true;
    }
}
