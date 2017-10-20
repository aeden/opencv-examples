package application;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class MainController {
	private static int cameraId = 0;

	@FXML
	private Button startButton;

	@FXML
	private ImageView currentFrame;

	private ScheduledExecutorService timer;
	private VideoCapture capture;
	private boolean cameraActive;

	public MainController() {
		this.capture = new VideoCapture();
		this.cameraActive = false;
	}

	@FXML
	protected void toggleCamera(ActionEvent event) {
		if (this.cameraActive) {
			System.out.println("Stopping camera " + cameraId);
			stopAcquisition();
			System.out.println("Camera is stopped");
			this.startButton.setText("Start Camera");
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
							currentFrame.imageProperty().set(imageToShow);
						});
					}
				};

				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

				// update the button content
				this.startButton.setText("Stop Camera");
			}
		}
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
					// Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY);
				}

			} catch (Exception e) {
				// log the error
				System.err.println("Exception during the image elaboration: " + e);
			}
		}

		return frame;
	}

	private void stopAcquisition() {
		if (this.timer != null && !this.timer.isShutdown()) {
			try {
				// stop the timer
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
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
