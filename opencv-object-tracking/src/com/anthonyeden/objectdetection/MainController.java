package com.anthonyeden.objectdetection;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
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

    private Size blurSize = new Size(7, 7);

    private Scalar boundingRectColor;
    private int boundingRectThickness = 4;
    private int minimumBoundingWidth = 20;
    private int minimumBoundingHeight = 20;

    private ObjectProperty<String> hsvValuesProp;
    private Scalar centerTargetColor = RED;
    private int centerTargetThickness = 10;

    private Scalar directionIndicatorColor = RED;
    private int directionIndicatorThickness = 10;

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
			// render the image
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
		    Imgproc.blur(frame, blurredImage, blurSize);

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

		    // fill in the mask that is used to find the objects
		    Core.inRange(hsvImage, minValues, maxValues, mask);

		    // show the mask output
		    Image maskImage = Utils.mat2Image(mask);
		    Platform.runLater(() -> {
			try {
			    this.maskImage.imageProperty().set(maskImage);
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

		    // render the morph output
		    Image morphImage = Utils.mat2Image(morphOutput);
		    Platform.runLater(() -> {
			try {
			    this.morphImage.imageProperty().set(morphImage);
			} catch (Exception e) {
			    System.err.println("Exception updating morph image: " + e);
			}
		    });

		    // find the object in the morph output and display the appropriate
		    // bounding and target details in the primary camera image
		    frame = findAndDrawObject(morphOutput, frame);
		}

	    } catch (Exception e) {
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

	// Draw boundaries if any are present
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

	    // draw the object bounding rectangle
	    if (boundingRect.width > minimumBoundingWidth && boundingRect.height > minimumBoundingHeight) {
		Imgproc.rectangle(frame, boundingRect.tl(), boundingRect.br(), boundingRectColor,
			boundingRectThickness);
	    }

	    Rect centerTarget = getCenterTargetRect(frame);

	    Rectangle r1 = new Rectangle(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height);
	    Rectangle r2 = new Rectangle(centerTarget.x, centerTarget.y, centerTarget.width, centerTarget.height);

	    // if the bounding rectangle and target intersect, draw the target
	    if (r1.intersects(r2)) {
		Imgproc.rectangle(frame, centerTarget.tl(), centerTarget.br(), centerTargetColor,
			centerTargetThickness);
		// System.out.println("stop");
	    } else {
		// what direction do we move?
		if (boundingRect.x > centerTarget.x + centerTarget.width) {
		    Imgproc.rectangle(frame, new Point(frame.width() - 20, 0), new Point(frame.width(), frame.height()),
			    directionIndicatorColor, directionIndicatorThickness);
		    // System.out.println("turn right");
		} else if (boundingRect.x < centerTarget.x) {
		    Imgproc.rectangle(frame, new Point(0, 0), new Point(20, frame.height()), directionIndicatorColor,
			    directionIndicatorThickness);
		    // System.out.println("turn left");
		}
	    }

	}
	return frame;
    };

    private Rect getCenterTargetRect(Mat frame) {
	int width = 200;
	int height = 100;
	int x = (frame.width() / 2) - (width / 2);
	int y = (frame.height() / 2) - (height / 2);
	return new Rect(x, y, width, height);
    }

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
