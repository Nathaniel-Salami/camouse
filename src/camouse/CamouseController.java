package camouse;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public class CamouseController {
    private static final int CAMERA_INDEX = 0;
    private static final int FRAME_PER_SECOND = 33;

    //member variables
    private VideoCapture videoCapture = new VideoCapture();
    private ScheduledExecutorService frameRateTimer;

    //Form elements
    private boolean cameraActive;
    @FXML
    private Button startButton;
    @FXML
    private ImageView originalImageView;
    @FXML
    private ImageView hsvReducedImageView;
    @FXML
    private ImageView finalImageView;


    public void startCamera(ActionEvent actionEvent) {
        if (!cameraActive) {
            this.videoCapture.open(CAMERA_INDEX);

            if (this.videoCapture.isOpened()) {
                this.cameraActive = true;
                this.frameRateTimer = Executors.newSingleThreadScheduledExecutor();
                this.frameRateTimer.scheduleAtFixedRate(() -> {
                    runOnFXThread(() -> processAndDisplay());
                }, 0, FRAME_PER_SECOND, TimeUnit.DAYS.MILLISECONDS);
            }
        }
    }

    private void processAndDisplay() {
        Mat originalFrame = getFrame();
        Image originalImage = matToImage(originalFrame);

        //TODO: There would be further processing here

        this.originalImageView.setImage(originalImage);
        this.hsvReducedImageView.setImage(originalImage);
        this.finalImageView.setImage(originalImage);
    }

    private Mat getFrame() {
        Mat frame = new Mat();
        if (this.videoCapture.isOpened()) {
            try {
                this.videoCapture.read(frame);
            } catch (Exception e) {
                System.err.println("Could not read frame: " + e);
            }
        }
        if (frame.empty()) {
            return null;
        }
        return frame;
    }

    private Image matToImage(Mat mat) {
        int width = mat.width();
        int height = mat.height();
        int channels = mat.channels();
        byte[] srcPixels = new byte[width * height * channels];
        byte[] targetPixels;
        BufferedImage image = null;

        mat.get(0, 0, srcPixels);
        if (mat.channels() > 1) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        } else {
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        }
        targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(srcPixels, 0, targetPixels, 0, srcPixels.length);

        try {
            return SwingFXUtils.toFXImage(image, null);
        } catch (Exception e) {
            System.err.println("Failed to convert Mat to JavaFX Image: " + e);
            return null;
        }
    }

    private void runOnFXThread(Runnable task) {
        Platform.runLater(task);
    }

    public void onClose() {
        if (this.frameRateTimer != null && !this.frameRateTimer.isShutdown()) {
            try {
                // stop the frameRateTimer
                this.frameRateTimer.shutdown();
                this.frameRateTimer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // log any exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }
        }

        if (this.videoCapture.isOpened()) {
            // release the camera
            this.videoCapture.release();
        }
    }
}
