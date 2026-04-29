package flowops.support;

import java.util.Arrays;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.IvGenerator;
import org.jasypt.iv.NoIvGenerator;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;

public class JasyptTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("사용법: encrypt <평문> 또는 decrypt <암호문>");
        }

        String password = getenvOrThrow("JASYPT_ENCRYPTOR_PASSWORD");
        String algorithm = System.getenv().getOrDefault(
                "JASYPT_ENCRYPTOR_ALGORITHM",
                "PBEWITHHMACSHA512ANDAES_256"
        );
        String outputType = System.getenv().getOrDefault("JASYPT_ENCRYPTOR_STRING_OUTPUT_TYPE", "base64");
        int iterations = Integer.parseInt(System.getenv().getOrDefault(
                "JASYPT_ENCRYPTOR_KEY_OBTENTION_ITERATIONS",
                "1000"
        ));
        String ivGeneratorClassName = System.getenv().getOrDefault(
                "JASYPT_ENCRYPTOR_IV_GENERATOR_CLASSNAME",
                "org.jasypt.iv.RandomIvGenerator"
        );

        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setAlgorithm(algorithm);
        encryptor.setKeyObtentionIterations(iterations);
        encryptor.setStringOutputType(outputType);
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setIvGenerator(createIvGenerator(ivGeneratorClassName));

        String mode = args[0];
        String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        switch (mode) {
            case "encrypt" -> System.out.println(encryptor.encrypt(value));
            case "decrypt" -> System.out.println(encryptor.decrypt(value));
            default -> throw new IllegalArgumentException("모드는 encrypt 또는 decrypt여야 합니다.");
        }
    }

    private static String getenvOrThrow(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 환경 변수가 필요합니다.");
        }
        return value;
    }

    private static IvGenerator createIvGenerator(String className) {
        return switch (className) {
            case "org.jasypt.iv.NoIvGenerator" -> new NoIvGenerator();
            case "org.jasypt.iv.RandomIvGenerator" -> new RandomIvGenerator();
            default -> throw new IllegalArgumentException("지원하지 않는 IV 생성기입니다: " + className);
        };
    }
}
