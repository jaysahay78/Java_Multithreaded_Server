package multithreaded;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class StaticFileReader {
    static String readTextFromPublic(String relativePath, PrintWriter out) {
        try {
            Path base = Path.of("public").toAbsolutePath().normalize();
            Path target = base.resolve(relativePath).normalize();
            if (!target.startsWith(base) || !Files.exists(target) || !Files.isRegularFile(target)) {
                return "ERR Not found";
            }
            for (String line : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                out.println(line);
            }
            return "OK";
        } catch (Exception e) {
            return "ERR IO";
        }
    }
}
