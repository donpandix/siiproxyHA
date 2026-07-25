package cl.cesarg.siiproxyHA.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositorySecretHygieneTest {

    private static final List<String> PRIVATE_KEY_MARKERS = List.of(
            "-----BEGIN " + "PRIVATE KEY-----",
            "-----BEGIN " + "ENCRYPTED PRIVATE KEY-----",
            "-----BEGIN " + "RSA PRIVATE KEY-----",
            "-----BEGIN " + "EC PRIVATE KEY-----",
            "-----BEGIN " + "DSA PRIVATE KEY-----",
            "-----BEGIN " + "OPENSSH PRIVATE KEY-----"
    );

    private static final Pattern CAF_PRIVATE_KEY_ELEMENT =
            Pattern.compile("<" + "RSASK(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);

    @Test
    void trackedFilesMustNotContainPrivateSigningMaterial() throws Exception {
        List<Path> trackedFiles = trackedFiles();
        List<String> violations = new ArrayList<>();

        for (Path file : trackedFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }

            String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            if (PRIVATE_KEY_MARKERS.stream().anyMatch(content::contains)
                    || CAF_PRIVATE_KEY_ELEMENT.matcher(content).find()) {
                violations.add(file.toString());
            }
        }

        assertTrue(
                violations.isEmpty(),
                "Tracked files contain private signing material: " + String.join(", ", violations)
        );
    }

    private List<Path> trackedFiles() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "ls-files", "-z")
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "Unable to enumerate tracked files with git");

        return Arrays.stream(new String(output, StandardCharsets.UTF_8).split("\\u0000"))
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .toList();
    }
}
