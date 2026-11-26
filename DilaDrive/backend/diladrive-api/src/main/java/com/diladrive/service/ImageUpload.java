package com.diladrive.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;

record ImageUpload(byte[] bytes, String extension) {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};

    static ImageUpload read(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid();
        }
        byte[] bytes = file.getBytes();
        if (bytes.length > MAX_BYTES) throw invalid();
        String extension = startsWith(bytes, JPEG) ? ".jpg" : startsWith(bytes, PNG) ? ".png" : null;
        if (extension == null) throw invalid();

        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null || !ImageIO.getImageReaders(input).hasNext()) throw invalid();
        }
        return new ImageUpload(bytes, extension);
    }

    String mediaType() {
        return ".jpg".equals(extension) ? "image/jpeg" : "image/png";
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) return false;
        }
        return true;
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid JPEG or PNG image of up to 10 MB.");
    }
}
