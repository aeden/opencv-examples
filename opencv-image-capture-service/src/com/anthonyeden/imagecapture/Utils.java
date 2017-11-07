package com.anthonyeden.imagecapture;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.Mat;

public class Utils {

    /**
     * Converts an OpenCV Mat to a BufferredImage. Handles grey scale and color
     * images.
     * 
     * @param original
     *            The original Mat image
     * @return The BufferedImage
     */
    protected static BufferedImage matToBufferedImage(Mat original) {
	BufferedImage image = null;
	int width = original.width(), height = original.height(), channels = original.channels();
	byte[] sourcePixels = new byte[width * height * channels];
	original.get(0, 0, sourcePixels);

	if (original.channels() > 1) {
	    image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
	} else {
	    image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
	}
	final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
	System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);

	return image;
    }
}
