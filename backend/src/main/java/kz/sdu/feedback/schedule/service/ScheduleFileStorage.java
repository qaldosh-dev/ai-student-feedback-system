package kz.sdu.feedback.schedule.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ScheduleFileStorage {
    private static final Logger log = LoggerFactory.getLogger(ScheduleFileStorage.class);
    private final Path directory;
    private final long maxBytes;

    public ScheduleFileStorage(@Value("${app.schedule-upload.directory}") String directory,
                               @Value("${spring.servlet.multipart.max-file-size}") DataSize maxSize) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes = maxSize.toBytes();
    }

    public record StoredFile(String key, String originalFilename) { }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty PDF file.");
        }
        if (file.getSize() > maxBytes) throw tooLarge();
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "";
        // Browser filenames are metadata only. Strip both Windows and Unix path prefixes.
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).strip();
        if (filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf") || filename.length() <= 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file with a valid .pdf filename.");
        }

        String key = UUID.randomUUID() + ".pdf";
        Path target = resolveKey(key);
        boolean created = false;
        try {
            Files.createDirectories(directory);
            // CREATE_NEW never overwrites an existing file, including a pre-existing symlink.
            try (var input = file.getInputStream();
                 var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw tooLarge();
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty PDF file.");
            }
            return new StoredFile(key, filename);
        } catch (IOException | RuntimeException failure) {
            if (created) deleteAfterFailure(key);
            if (failure instanceof ResponseStatusException validation) throw validation;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not store the upload. Please try again.");
        }
    }

    public void deleteAfterFailure(String key) {
        try { Files.deleteIfExists(resolveKey(key)); }
        catch (IOException failure) {
            // Do not log student filenames, contents or private filesystem paths.
            log.error("Could not clean up a failed schedule upload; local storage needs attention.");
        }
    }

    // Only a server-generated key is accepted; this path is never returned through the API.
    public Path fileForValidation(String key) { return resolveKey(key); }

    private Path resolveKey(String key) {
        if (!key.matches("[0-9a-f-]{36}\\.pdf")) throw new IllegalArgumentException("Invalid storage key");
        Path target = directory.resolve(key).normalize();
        if (!directory.equals(target.getParent())) throw new IllegalArgumentException("Invalid storage key");
        return target;
    }

    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF must not exceed 10 MB.");
    }
}
