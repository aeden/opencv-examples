package com.anthonyeden.objectdetection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class MainController {
	public static Scalar BLUE = new Scalar(255, 0, 0);
	public static Scalar GREEN = new Scalar(0, 255, 0);
	public static Scalar RED = new Scalar(0, 0, 255);

	private static int cameraId = 0;

	private static float fps = 10;

	@FXML
	private Button cameraButton;

	@FXML
	private ImageView originalFrame;

	@FXML
	private ImageView maskImage;

	@FXML
	private ImageView morphImage;

	@FXML
	private Slider hueStartSlider;

	@FXML
	private Slider hueStopSlider;

	@FXML
	private Slider saturationStartSlider;

	@FXML
	private Slider saturationStopSlider;

	@FXML
	private Slider valueStartSlider;

	@FXML
	private Slider valueStopSlider;

	@FXML
	private Label hsvValuesLabel;

	private ScheduledExecutorService timer;
	private VideoCapture capture;
	private boolean cameraActive;
	private Rect lastBoundingRect;
	private Scalar boundingRectColor;
	private Rect centerTarget;

	private ObjectProperty<String> hsvValuesProp;
	private Scalar centerTargetColor = RED;
	private int centerTargetThickness = 10;

	public MainController() {
		this.capture = new VideoCapture();
		this.cameraActive = false;
		this.boundingRectColor = BLUE;
	}

	@FXML
	protected void toggleCamera(ActionEvent event) {
		System.out.println("Toggle camera");
		hsvValuesProp = new SimpleObjectProperty<>();
		this.hsvValuesLabel.textProperty().bind(hsvValuesProp);

		if (this.cameraActive) {
			System.out.println("Stopping camera " + cameraId);
			stopAcquisition();
			System.out.println("Camera is stopped");
			this.cameraButton.setText("Start Camera");
		} else {
			System.out.println("Starting camera " + cameraId);
			this.capture.open(cameraId);

			if (this.capture.isOpened()) {
				this.cameraActive = true;
				System.out.println("Camera is running");
				Runnable frameGrabber = new Runnable() {
					@Override
					public void run() {
						Mat frame = grabFrame();
						Image imageToShow = Utils.mat2Image(frame);
						Platform.runLater(() -> {
							originalFrame.imageProperty().set(imageToShow);
						});
					}
				};

				System.out.println("Starting frame grabber");
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, getFrameGrabSchedule(), TimeUnit.MILLISECONDS);

				System.out.println("Frame grabber is running");
				// update the button content
				this.cameraButton.setText("Stop Camera");
			}
		}
	}

	protected long getFrameGrabSchedule() {
		long frameGrabSchedule = (long) ((1f / fps) * 1000f);

		System.out.println("Current frame grab rate: " + (long) fps + "fps");
		System.out.println("Calculated frame grab schedule: " + frameGrabSchedule + "ms");

		return frameGrabSchedule;
	}

	protected Mat grabFrame() {
		Mat frame = new Mat();

		// check if the capture is open
		if (this.capture.isOpened()) {
			try {
				// read the current frame
				this.capture.read(frame);

				// if the frame is not empty, process it
				if (!frame.empty()) {
					// System.out.println("Processing frame");
					Mat blurredImage = new Mat();
					Mat hsvImage = new Mat();
					Mat mask = new Mat();
					Mat morphOutput = new Mat();

					// remove some noise
					Imgproc.blur(frame, blurredImage, new Size(7, 7));

					// convert the frame to HSV
					Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);

					// get thresholding values from the UI
					// remember: H ranges 0-180, S and V range 0-255
					Scalar minValues = new Scalar((long) this.hueStartSlider.getValue(),
							(long) this.saturationStartSlider.getValue(), (long) this.valueStartSlider.getValue());
					Scalar maxValues = new Scalar((long) this.hueStopSlider.getValue(),
							(long) this.saturationStopSlider.getValue(), (long) this.valueStopSlider.getValue());

					// show the current selected HSV range
					String valuesToPrint = "Hue range: " + minValues.val[0] + "-" + maxValues.val[0]
							+ "\tSaturation range: " + minValues.val[1] + "-" + maxValues.val[1] + "\tValue range: "
							+ minValues.val[2] + "-" + maxValues.val[2];
					Platform.runLater(() -> {
						try {
							hsvValuesProp.set(valuesToPrint);
						} catch (Exception e) {
							System.err.println("Exception setting HSV values: " + e);
						}
					});

					// threshold HSV image to select tennis balls
					Core.inRange(hsvImage, minValues, maxValues, mask);
					// show the partial output
					Platform.runLater(() -> {
						try {
							this.maskImage.imageProperty().set(Utils.mat2Image(mask));
						} catch (Exception e) {
							System.err.println("Exception updating mask image: " + e);
						}
					});

					// morphological operators
					// dilate with large element, erode with small ones
					Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
					Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));

					Imgproc.erode(mask, morphOutput, erodeElement);
					Imgproc.erode(morphOutput, morphOutput, erodeElement);

					Imgproc.dilate(morphOutput, morphOutput, dilateElement);
					Imgproc.dilate(morphOutput, morphOutput, dilateElement);

					Platform.runLater(() -> {
						try {
							this.morphImage.imageProperty().set(Utils.mat2Image(morphOutput));
						} catch (Exception e) {
							System.err.println("Exception updating morph image: " + e);
						}
					});

					int width = 200;
					int height = 100;
					int x = (frame.width() / 2) - (width / 2);
					int y = (frame.height() / 2) - (height / 2);
					this.centerTarget = new Rect(x, y, width, height);
					Imgproc.rectangle(frame, centerTarget.tl(), centerTarget.br(), centerTargetColor, centerTargetThickness);
					
					frame = findAndDrawObject(morphOutput, frame);
				}

			} catch (Exception e) {
				// log the error
				System.err.println("Exception during the image elaboration: " + e);
			}
		}

		return frame;
	}

	private Mat findAndDrawObject(Mat morphOutput, Mat frame) {
		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierarchy = new Mat();

		// Find contours
		Imgproc.findContours(morphOutput, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);

		// Draw boundries if any are present
		if (hierarchy.size().height > 0 && hierarchy.size().width > 0) {
			for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
				frame = drawObjectBoundry(frame, contours, idx);
			}
		}

		return frame;
	}

	private Mat drawObjectBoundry(Mat frame, List<MatOfPoint> contours, int idx) {
		// Imgproc.drawContours(frame, contours, idx, RED, 5);
		for (int i = 0; i < contours.size(); i++) {
			Rect boundingRect = Imgproc.boundingRect(contours.get(i));

			if (lastBoundingRect != null) {
				if (boundingRect.x - lastBoundingRect.x > 20) {
					System.out.println("Object moving right");
				} else if (boundingRect.x - lastBoundingRect.x < -20) {
					System.out.println("Object moving left");
				} else {
					// System.out.println("Object is stationary");
				}
			}
			
			if (boundingRect.width > 20 && boundingRect.height > 20) {
				int boundingRectThickness = 10;
				Imgproc.rectangle(frame, boundingRect.tl(), boundingRect.br(), boundingRectColor, boundingRectThickness);
				
				this.lastBoundingRect = boundingRect;
			} else {
				this.lastBoundingRect = null;
			}
		}
		return frame;
	};

	private void stopAcquisition() {
		if (this.timer != null && !this.timer.isShutdown()) {
			try {
				System.out.println("Stopping acquisition");
				// stop the timer
				this.timer.shutdown();
				this.timer.awaitTermination(getFrameGrabSchedule(), TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// log any exception
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
			}
		}

		if (this.capture.isOpened()) {
			// release the camera
			this.capture.release();
		}

		this.cameraActive = false;
	}

	public void setClosed() {
		stopAcquisition();
	}
}
