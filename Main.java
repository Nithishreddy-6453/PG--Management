import at.favre.lib.crypto.bcrypt.BCrypt;

public class Main {
    public static void main(String[] args) {
        String pin = "1234";
        String hash = BCrypt.withDefaults().hashToString(12, pin.toCharArray());
        System.out.println("Hash: " + hash);
        boolean verify1 = BCrypt.verifyer().verify(pin.toCharArray(), hash.toCharArray()).verified;
        System.out.println("Verify with toCharArray: " + verify1);
        boolean verify2 = BCrypt.verifyer().verify(pin.toCharArray(), hash).verified;
        System.out.println("Verify with string: " + verify2);
    }
}
