public class Utility {

    public static Integer TryParseInt(String s) {
        try {
            return new Integer(Integer.parseInt(s));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

}
