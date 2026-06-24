package com.guobang.transport.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

final class ImageThumbnailer {
    private static final int MAX_WIDTH = 120;
    private static final int MAX_HEIGHT = 90;

    private ImageThumbnailer() {
    }

    static byte[] build(byte[] data) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) {
                return null;
            }
            double scale = Math.min((double) MAX_WIDTH / src.getWidth(), (double) MAX_HEIGHT / src.getHeight());
            if (scale > 1) {
                scale = 1;
            }
            int width = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(src.getHeight() * scale));
            BufferedImage thumb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, width, height, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "jpg", out);
            return out.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }
}
