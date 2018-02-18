package camouse;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class CamouseController {
    //constants
    private static final int BLUR_SIZE = 20;
    private static final double CONTOUR_APPROX_FACTOR = 0.1;
    private static final double SMALLEST_AREA = 0;
    private static final int THUMB = 0;
    private static final int INDEX_FINGER = 1;

    //member vars
    private Mat currentFrame;
    List<Point> startPoints = new ArrayList<>();
    List<Point> valleyPoints = new ArrayList<>();
    List<Point> endPoints = new ArrayList<>();
    List<Double> depths = new ArrayList<>();
    private Point[] initialPosition = {new Point(), new Point()};
    private Point[] currentPosition = new Point[2];
    private boolean _isThumbExtended;


    // FXML camera button
    @FXML
    private Button cameraButton;
    // the FXML area for showing the current frame
    @FXML
    private ImageView originalImageView;
    // the FXML area for showing the mask
    @FXML
    private ImageView maskImageView;
    // the FXML area for showing the output of the morphological operations
    @FXML
    private ImageView morphImageView;
    // FXML slider for setting HSV ranges
    @FXML
    private Slider hueStart;
    @FXML
    private Slider hueEnd;
    @FXML
    private Slider saturationStart;
    @FXML
    private Slider saturationEnd;
    @FXML
    private Slider valueStart;
    @FXML
    private Slider valueEnd;

    @FXML
    private Slider erodeNum;
    @FXML
    private Slider dilateNum;

    // FXML label to show the current values set with the sliders
    @FXML
    private Label hsvCurrentValues;

    // a timer for acquiring the video stream
    private ScheduledExecutorService timer;
    // the OpenCV object that performs the video capture
    private VideoCapture capture = new VideoCapture();
    // a flag to change the button behavior
    private boolean cameraActive;

    // property for object binding
    private ObjectProperty<String> hsvValuesProp;


    //Public API
    public Point getFingerPosition(int fingerId){
        return currentPosition[fingerId].clone();
    }

    public Point getMovement(int fingerId) {
        return new Point(currentPosition[fingerId].x - initialPosition[fingerId].x,
          currentPosition[fingerId].y - initialPosition[fingerId].y);
    }


    public boolean isThumbExtended() {
        return _isThumbExtended;
    }

    //private methods
    @FXML
    private void calibrateInitial() throws Exception {
        if(currentPosition[THUMB] == null){
            throw new Exception("Cannot detect thumb. Cannot calibrate");
        }
        initialPosition[THUMB] = currentPosition[THUMB].clone();
        initialPosition[INDEX_FINGER] = currentPosition[INDEX_FINGER].clone();
        Imgproc.circle(currentFrame, initialPosition[INDEX_FINGER], 5, new Scalar(100,100,100), 4);
    }


    @FXML
    private void startCamera() {
        // bind a text property with the string containing the current range of
        // HSV values for object detection
        hsvValuesProp = new SimpleObjectProperty<>();
        this.hsvCurrentValues.textProperty().bind(hsvValuesProp);

        // set a fixed width for all the image to show and preserve image ratio
        this.imageViewProperties(this.originalImageView, 400);
        this.imageViewProperties(this.maskImageView, 200);
        this.imageViewProperties(this.morphImageView, 200);


        if (!this.cameraActive) {
            // start the video capture
            this.capture.open(0);

            // is the video stream available?
            if (this.capture.isOpened()) {
                this.cameraActive = true;

                // grab a frame every 33 ms (30 frames/sec)
                Runnable frameGrabber = () -> {
                    // effectively grab and process a single frame
                    this.currentFrame = grabFrame();

//                    Imgproc.circle(currentFrame, currentPosition[INDEX_FINGER], 10, new Scalar(0,0,250), 3);
//                    if(isThumbExtended()){
//                        Imgproc.circle(currentFrame, currentPosition[THUMB], 10, new Scalar(0,0,250), 3);
//                    }

                    // convert and show the frame
                    Image imageToShow = CamouseController.mat2Image(this.currentFrame);
                    updateImageView(originalImageView, imageToShow);


                };
                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                // update the button content
                this.cameraButton.setText("Stop Camera");
            } else {
                // log the error
                System.err.println("Failed to open the camera connection...");
            }
        } else {
            // the camera is not active at this point
            this.cameraActive = false;
            // update again the button content
            this.cameraButton.setText("Start Camera");

            // stop the timer
            this.stopAcquisition();
        }
    }

    private Point getTopMostPoint(MatOfPoint contour, MatOfInt convexHullIndices, MatOfPoint convexHullPoints) {
        double topMostPointY = Double.POSITIVE_INFINITY;
        int topMostPointIndex = -1;
        for (int i = 0; i < convexHullIndices.size().height; i++) {
            int index = (int) convexHullIndices.get(i, 0)[0];
            double[] point = new double[]{
              contour.get(index, 0)[0], contour.get(index, 0)[1]
            };
            if (point[1] < topMostPointY) {
                topMostPointY = point[1];
                topMostPointIndex = i;
            }
            convexHullPoints.put(i, 0, point);
        }
        return new Point(convexHullPoints.get(topMostPointIndex, 0));
    }

    private List<Point> reduceFingerTips() {
        ArrayList<Point> fingerPoints = new ArrayList<>();
        float MIN_FINGER_DEPTH = 10;
        float MAX_FINGER_ANGLE = 60;
        int numOfPoints = startPoints.size();
        for (int i = 0; i < numOfPoints; i++) {
            if (depths.get(i) < MIN_FINGER_DEPTH) continue;

            int prevIndex = (i == 0) ? (numOfPoints - 1) : (i - 1);
            int nextIndex = (i == numOfPoints - 1) ? 0 : (i + 1);
            int angle = angleBetween(startPoints.get(i), valleyPoints.get(prevIndex), valleyPoints.get(nextIndex));

            if (angle > MAX_FINGER_ANGLE) continue;

            fingerPoints.add(startPoints.get(i));
        }
        return fingerPoints;
    }

    private void drawConvexHull(MatOfPoint contour) {
        List<MatOfPoint> list = new ArrayList<>();
        list.add(contour);
        Imgproc.drawContours(this.currentFrame, list, 0, new Scalar(0, 255, 0), 3);
    }

    private Mat grabFrame() {
        this.currentFrame = new Mat();
        // check if the capture is open
        if (this.capture.isOpened()) {
            try {
                // read the current this.currentFrame
                this.capture.read(this.currentFrame);
                // if the this.currentFrame is not empty, process it
                if (!this.currentFrame.empty()) {
                    // init
                    Mat blurredImage = new Mat();
                    Mat hsvImage = new Mat();
                    Mat mask = new Mat();
                    Mat morphOutput = new Mat();
                    // remove some noise

                    Imgproc.blur(this.currentFrame, blurredImage, new Size(20, 20));

                    // convert the this.currentFrame to HSV
                    Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);
                    //hsvImage = blurredImage.clone();
                    // get thresholding values from the UI
                    // remember: H ranges 0-180, S and V range 0-255
                    Scalar minValues = new Scalar(this.hueStart.getValue(), this.saturationStart.getValue(),
                      this.valueStart.getValue());
                    Scalar maxValues = new Scalar(this.hueEnd.getValue(), this.saturationEnd.getValue(),
                      this.valueEnd.getValue());

                    // show the current selected HSV range
                    String valuesToPrint = "Hue range: " + minValues.val[0] + "-" + maxValues.val[0]
                      + "\tSaturation range: " + minValues.val[1] + "-" + maxValues.val[1] + "\tValue range: "
                      + minValues.val[2] + "-" + maxValues.val[2];
                    CamouseController.onFXThread(this.hsvValuesProp, valuesToPrint);
                    // threshold HSV image to select tennis balls
                    Core.inRange(hsvImage, minValues, maxValues, mask);
                    // show the partial output
                    this.updateImageView(this.maskImageView, CamouseController.mat2Image(mask));

                    // morphological operators
                    // dilate with large element, erode with small ones

                    Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
                    Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));


                    Imgproc.erode(mask, morphOutput, erodeElement);
                    for (int i = 1; i < Math.round(erodeNum.getValue()); i++) {
                        Imgproc.erode(morphOutput, morphOutput, erodeElement);
                    }

                    Imgproc.dilate(morphOutput, morphOutput, dilateElement);
                    for (int i = 1; i < Math.round(dilateNum.getValue()); i++) {
                        Imgproc.dilate(morphOutput, morphOutput, dilateElement);
                    }

                    // show the partial output
                    this.updateImageView(this.morphImageView, CamouseController.mat2Image(morphOutput));

                    // find the tennis ball(s) contours and show them
                    this.currentFrame = this.findContourAndDraw(this.currentFrame, morphOutput);

                }

            } catch (Exception e) {
                // log the (full) error
                System.err.print("Exception during the image elaboration...");
                e.printStackTrace();
            }
        }

        return this.currentFrame;
    }

    private Mat findContourAndDraw(Mat frame, Mat maskedImage) {
        // init
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        // find contours
        Imgproc.findContours(maskedImage, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        double maxArea = SMALLEST_AREA;
        int biggestContourIdx = -1;
        // if any contour exist...

        if (hierarchy.size().height > 0 && hierarchy.size().width > 0) {
            // for each contour, display it in blue
            for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
                double area = Imgproc.boundingRect(contours.get(idx)).area();

                if (area > maxArea) {
                    maxArea = area;
                    biggestContourIdx = idx;
                }
            }
        }
        if (biggestContourIdx > -1) {
            Imgproc.drawContours(frame, contours, biggestContourIdx, new Scalar(250, 0, 0), 1);
            findFingerTips(contours.get(biggestContourIdx), frame);
        }

        return frame;
    }

    private void findFingerTips(MatOfPoint contour, Mat frame) {
        //Approximate contour
        MatOfPoint2f contourFloat = new MatOfPoint2f(contour.toArray());
        MatOfPoint2f approxContour = new MatOfPoint2f();
        double epsilon = CONTOUR_APPROX_FACTOR * Imgproc.arcLength(contourFloat, false);
        Imgproc.approxPolyDP(contourFloat, approxContour, epsilon, true);
        contour = new MatOfPoint(approxContour.toArray());

        //calculate convex hull
        MatOfInt convexHullIndices = new MatOfInt();
        Imgproc.convexHull(contour, convexHullIndices, false);
        MatOfPoint convexHullPoints = new MatOfPoint();
        convexHullPoints.create((int) convexHullIndices.size().height, 1, CvType.CV_32SC2);
        //draw convex hull
        drawConvexHull(convexHullPoints);


        //detect contour defects (valleys)
        MatOfInt4 defects = new MatOfInt4();
        Imgproc.convexityDefects(contour, convexHullIndices, defects);

        //set index finger
        Point topMostPoint = getTopMostPoint(contour, convexHullIndices, convexHullPoints);
        currentPosition[INDEX_FINGER] = topMostPoint.clone();

        long numDefects = defects.total();
        //if no defects, thumb position is null
        if (numDefects == 0) {
            _isThumbExtended = false;
            currentPosition[THUMB] = null;
        }
        else { //otherwise we have to calculate thumb position  using defect
            _isThumbExtended = true;
            //update defect start, bottom, and end positions + update defect depth
            startPoints.clear();
            valleyPoints.clear();
            depths.clear();
            for (int i = 0; i < numDefects; i++) {
                double[] defectStartCoords = contour.get((int) Math.round(defects.get(i, 0)[0]), 0);
                double[] valleyCoods = contour.get((int) Math.round(defects.get(i, 0)[2]), 0);
                double[] defectEndCoords = contour.get((int) Math.round(defects.get(i, 0)[1]), 0);
                Point defectStartPoint = new Point(defectStartCoords);
                Point valleyPoint = new Point(valleyCoods);
                Point defectEndPoint = new Point(defectEndCoords);
                double depth = defects.get(i, 0)[2];

                startPoints.add(defectStartPoint);
                valleyPoints.add(valleyPoint);
                endPoints.add(defectEndPoint);
                depths.add(depth);
            }

            currentPosition[THUMB] = startPoints.get(0).clone();

//            //reduce doesn't work
//            List<Point> reducedList = reduceFingerTips();
//            if(reducedList.size() ==0){
//                _isThumbExtended = false;
//                currentPosition[THUMB] = null;
//            }
//            else{
//                currentPosition[THUMB] = reduceFingerTips().get(0).clone();
//            }
        }
    }

    private int angleBetween(Point tip, Point next, Point prev) {
        return Math.abs((int) Math.round(
          Math.toDegrees(
            Math.atan2(next.x - tip.x, next.y - tip.y) -
              Math.atan2(prev.x - tip.x, prev.y - tip.y))));
    }

    private void imageViewProperties(ImageView image, int dimension) {
        // set a fixed width for the given ImageView
        image.setFitWidth(dimension);
        // preserve the image ratio
        image.setPreserveRatio(true);
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
    }

    private void updateImageView(ImageView view, Image image) {
        CamouseController.onFXThread(view.imageProperty(), image);
    }


    protected void setClosed() {
        this.stopAcquisition();
    }


    public static Image mat2Image(Mat frame) {
        try {
            return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
        } catch (Exception e) {
            System.err.println("Cannot convert the Mat obejct: " + e);
            return null;
        }
    }

    public static <T> void onFXThread(final ObjectProperty<T> property, final T value) {
        Platform.runLater(() -> {
            property.set(value);
        });
    }

    private static BufferedImage matToBufferedImage(Mat original) {
        // init
        BufferedImage image;
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
