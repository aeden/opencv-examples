package com.anthonyeden.imagecapture;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public class Service {

    public static String IMAGE_OUTPUT_TYPE = "png";
    public static String FILE_PREFIX = "frame-";

    public static void main(String[] args) throws InterruptedException {
	// Load the opencv native library
	System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

	// Start the image service
	Service service = new Service(outputDirectory(args));
	service.serve();
    }

    private static String outputDirectory(String[] args) {
	String outputDirectory = null;
	if (args.length > 0) {
	    outputDirectory = args[0];
	} else {
	    outputDirectory = System.getProperty("user.home");
	}
	return outputDirectory;
    }

    private String outputDirectory;

    public Service(String outputDirectory) {
	this.outputDirectory = outputDirectory;
    }

    public void serve() throws InterruptedException {
	VideoCapture capture = new VideoCapture();
	capture.open(0);
	if (capture.isOpened()) {
	    Runnable frameGrabber = new Runnable() {
		private int frameNumber = 1;

		@Override
		public void run() {
		    try {
			Mat frame = grabFrame(capture);
			BufferedImage image = Utils.matToBufferedImage(frame);
			System.out.println("Image acquired: " + image.getWidth(null) + " x " + image.getHeight(null));
			File outputFile = new File(outputDirectory,
				FILE_PREFIX + frameNumber + "." + IMAGE_OUTPUT_TYPE);
			ImageIO.write(image, IMAGE_OUTPUT_TYPE, outputFile.getAbsoluteFile());
			frameNumber = frameNumber + 1;
		    } catch (IOException e) {
			System.out.println("Failed to render frame " + frameNumber + ": " + e.getMessage());
		    }
		}
	    };

	    double fps = 0.1;
	    long frameGrabSchedule = (long) ((1f / fps) * 1000f);
	    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
	    timer.scheduleAtFixedRate(frameGrabber, 0, frameGrabSchedule, TimeUnit.MILLISECONDS);
	    while (!timer.isShutdown()) {
		Thread.sleep(1000);
	    }
	} else {
	    System.out.println("Cannot open camera");
	}
    }

    private Mat grabFrame(VideoCapture capture) {
	Mat frame = new Mat();

	if (capture.isOpened()) {
	    try {
		capture.read(frame);
		if (!frame.empty()) {
		    frame = processFrame(frame);
		}
	    } catch (Exception e) {
		System.err.println("Exception during the image elaboration: " + e);
	    }
	}

	return frame;
    }

    private Mat processFrame(Mat frame) {
	return frame;
    }

}
