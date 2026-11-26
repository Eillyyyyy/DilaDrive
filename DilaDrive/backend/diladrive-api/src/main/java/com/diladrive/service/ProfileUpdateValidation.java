package com.diladrive.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ProfileUpdateValidation {
    private static final String IMAGE_ERROR = "Choose a valid PNG, JPEG or WebP image smaller than 900 KB.";

    private ProfileUpdateValidation() {}

    static String phone(String value) {
        String phone = value.trim();
        if (!phone.matches("\\+?[0-9](?:[0-9 -]*[0-9])?")
                || !phone.replaceAll("[ -]", "").matches("(?:0|\\+60|60)[1-9][0-9]{7,9}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid Malaysian phone number.");
        }
        return phone;
    }

    static String mobileImage(String image) {
        if (image.isEmpty()) return null;
        if (!image.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, IMAGE_ERROR);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
            boolean png = bytes.length >= 8 && bytes[0] == (byte) 137 && bytes[1] == 80
                    && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13
                    && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 255
                    && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
            boolean webp = bytes.length >= 12
                    && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            boolean matches = image.startsWith("data:image/png;") && png
                    || image.startsWith("data:image/jpeg;") && jpeg
                    || image.startsWith("data:image/webp;") && webp;
            if (bytes.length > 900 * 1024 || !matches) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, IMAGE_ERROR);
        }
        return image;
    }

    static String customerImage(String image) {
        String validated = mobileImage(image);
        if (validated == null) return null;
        byte[] bytes = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
        try {
            if (image.startsWith("data:image/webp;")) {
                if (!hasWebpFrame(bytes)) throw new IOException("Invalid WebP container");
            } else {
                try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(stream);
                    if (!readers.hasNext()) throw new IOException("Unknown image");
                    var reader = readers.next();
                    try {
                        reader.setInput(stream);
                        if ((long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000
                                || reader.read(0) == null) throw new IOException("Invalid image");
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, IMAGE_ERROR);
        }
        return validated;
    }

    // The JDK has no built-in WebP decoder. Validate RIFF lengths and the still-image frame header.
    private static boolean hasWebpFrame(byte[] bytes) {
        var data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (Integer.toUnsignedLong(data.getInt(4)) + 8 != bytes.length) return false;
        boolean frame = false;
        int offset = 12;
        while (offset <= bytes.length - 8) {
            long length = Integer.toUnsignedLong(data.getInt(offset + 4));
            int start = offset + 8;
            long next = start + length + (length & 1);
            if (next > bytes.length) return false;
            String chunk = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            if ("VP8 ".equals(chunk) && length > 10 && (bytes[start] & 1) == 0
                    && bytes[start + 3] == (byte) 0x9d && bytes[start + 4] == 1
                    && bytes[start + 5] == 0x2a) frame = true;
            if ("VP8L".equals(chunk) && length > 5 && bytes[start] == 0x2f
                    && (bytes[start + 4] & 0xe0) == 0) frame = true;
            offset = (int) next;
        }
        return frame && offset == bytes.length;
    }
}
