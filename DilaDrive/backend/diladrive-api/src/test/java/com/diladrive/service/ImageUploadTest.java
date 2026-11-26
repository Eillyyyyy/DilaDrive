package com.diladrive.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ImageUploadTest {
    @Test
    void validatesContentRegardlessOfClientFilenameAndMediaType() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", buffer);
        var image = ImageUpload.read(new MockMultipartFile(
                "file", "../../client.pdf", "application/octet-stream", buffer.toByteArray()));
        assertEquals(".png", image.extension());
        assertEquals("image/png", image.mediaType());
    }

    @Test
    void rejectsOversizeAndNonImagesIncludingPdf() {
        for (byte[] invalid : new byte[][] {new byte[10 * 1024 * 1024 + 1],
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII), new byte[] {1, 2, 3}}) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> ImageUpload.read(
                    new MockMultipartFile("file", "fake.png", "image/png", invalid))).getStatusCode().value());
        }
    }
}
