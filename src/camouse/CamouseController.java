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
    private static final float cameraX = 640;
    private static final float cameraY = 460;

    private long theTime;
    private long timeElapsed;

    //member vars
    private Mat currentFrame;
    List<Point> startPoints = new ArrayList<>();
    List<Point> valleyPoints = new ArrayList<>();
    List<Point> endPoints = new ArrayList<>();
    List<Double> depths = new ArrayList<>();
    private Point[] initialPosition = {new Point(-1, -1), new Point(-1, -1)};
    private Point[] currentPosition = new Point[2];

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
    private TempMain tempMain;

    // property for object binding
    private ObjectProperty<String> hsvValuesProp;

    public boolean isCalibrated = false;

    /*
    PUBLIC API
     */
    private boolean _isThumbExtended = false;
    public boolean isThumbExtended() {
        return _isThumbExtended;
        //return currentPosition[THUMB].x < 0;
    }

    public Point getCurrentPosition(int fingerId) {
        return currentPosition[fingerId];
    }

    public Point getDisplacement(int fingerId) {
        return new Point(currentPosition[fingerId].x - initialPosition[fingerId].x,
          currentPosition[fingerId].y - initialPosition[fingerId].y);
    }
    /*
    END OF PUBLIC API
     */

    @FXML
    private void calibrateInitial() {
        if (this.cameraActive) {

            if(currentPosition[INDEX_FINGER] != null){
                initialPosition[INDEX_FINGER] = currentPosition[INDEX_FINGER].clone();
                if (isThumbExtended()) {
                    initialPosition[THUMB] = currentPosition[THUMB].clone();
                }
                /*tempMain.init[0]= (float)(initialPosition[INDEX_FINGER].x);
                tempMain.init[0]= (float)(float)(initialPosition[INDEX_FINGER].y);*/
                tempMain = new TempMain((float) (initialPosition[INDEX_FINGER].x), (float) (initialPosition[INDEX_FINGER].y), cameraX, cameraY);
                //System.out.println("INITIAL: " + initialPosition[INDEX_FINGER]);
                isCalibrated = true;
            }else{
                System.err.println("ERROR! Nothing detected!! Please position the selected glove in front of the camera...");
            }

        } else {
            System.err.println("ERROR! Please activate the mouse before calibration");
        }
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

        //detect click
        //i(!isThumbExtended() && !isClicking){
        //pointer
        //isClicking = true;
        //System.out.println("POINTING");
        //theTime = System.nanoTime();
        //
    }


    private Mat grabFrame() {
        this.currentFrame = new Mat();
        // check if the capture is open
        if (this.capture.isOpened()) {
            try {
                // read the current this.currentFrame
                this.capture.read(this.currentFrame);
//                System.out.print("Resolution: " + this.currentFrame.width() + "," + this.currentFrame.height());
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

//                    System.out.println("MIN" + minValues);
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
//        maybe use approx contour;
        MatOfPoint2f contourFloat = new MatOfPoint2f(contour.toArray());
        MatOfPoint2f approxContour = new MatOfPoint2f();
        double epsilon = 0.05 * Imgproc.arcLength(contourFloat, false);
        Imgproc.approxPolyDP(contourFloat, approxContour, epsilon, true);
        contour = new MatOfPoint(approxContour.toArray());


        MatOfInt convexHullIndices = new MatOfInt();
        MatOfInt4 defects = new MatOfInt4();
        Imgproc.convexHull(contour, convexHullIndices, false);

        MatOfPoint convexHullPoints = new MatOfPoint();
        convexHullPoints.create((int) convexHullIndices.size().height, 1, CvType.CV_32SC2);

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
        Point topMostPoint = new Point(convexHullPoints.get(topMostPointIndex, 0));
        Imgproc.circle(frame, topMostPoint, 10, new Scalar(0, 255, 0), 3);
        _isThumbExtended = false;
        currentPosition[THUMB] = new Point(-1, -1);
        currentPosition[INDEX_FINGER] = topMostPoint.clone();

        //draw hull
        ArrayList<MatOfPoint> hullList = new ArrayList<>();
        hullList.add(0, convexHullPoints);
        Imgproc.drawContours(frame, hullList, 0, new Scalar(0, 255, 0), 3);

        Imgproc.convexityDefects(contour, convexHullIndices, defects);
        startPoints.clear();
        valleyPoints.clear();
        depths.clear();
        long numDefects = defects.total();
        float MIN_FINGER_DEPTH = 90;

//        System.out.println("DEFECT: " + defects.total());
        for (int i = 0; i < defects.total(); i++) {
            double[] defectStartCoords = contour.get((int) Math.round(defects.get(i, 0)[0]), 0);
            double[] defectEndCoords = contour.get((int) Math.round(defects.get(i, 0)[1]), 0);
            double[] valleyCoods = contour.get((int) Math.round(defects.get(i, 0)[2]), 0);
            Point defectStartPoint = new Point(defectStartCoords);
            Point defectEndPoint = new Point(defectEndCoords);
            Point valleyPoint = new Point(valleyCoods);

            double depth = defects.get(i, 0)[3] / 256;

            Imgproc.circle(frame, defectStartPoint, 10, new Scalar(0, 0, 255), 3);
            _isThumbExtended = true;
            currentPosition[THUMB] = defectStartPoint.clone();
            //Imgproc.circle(frame, defectEndPoint, 10, new Scalar(0, 255, 0), 3);
//            if (depth < MIN_FINGER_DEPTH) {
//                continue;
//            }
            startPoints.add(defectStartPoint);
            valleyPoints.add(valleyPoint);
            depths.add(depth);
        }

        if (isCalibrated) {
            scroller.init((float) (currentPosition[INDEX_FINGER].x), (float) (currentPosition[INDEX_FINGER].y));
//            tempMain.update((float) (currentPosition[INDEX_FINGER].x), (float) (currentPosition[INDEX_FINGER].y));
////
//            ScrollEventTest scrollTest = new ScrollEventTest((float) (initialPosition[INDEX_FINGER].x), (float) (initialPosition[INDEX_FINGER].y));
//            float[] diff = scale();

              scroller.mouseMovement((float)getDisplacement(INDEX_FINGER).x, (float)getDisplacement(INDEX_FINGER).y);

//            if (!isThumbExtended()) {
                cases();
//            }
        }
        reduceFingerTips(frame);
    }

    ScrollEventTest scroller = new ScrollEventTest();

    private boolean isClicking = false;
    private boolean isDragging = false;
    long clickStart;
    long clickEnd;
    long clickElapsed;


    boolean mousePress = false;
    private void cases() {

        if(!isThumbExtended() && !mousePress){
            mousePress = true;
            clickStart = System.currentTimeMillis();
        }
        if(!isThumbExtended() && mousePress){
            clickEnd = System.currentTimeMillis();
        }
        if(isThumbExtended() &&  mousePress){
            mousePress = false;
            clickElapsed = clickEnd - clickStart;
            if(clickElapsed > 2000){
                System.out.println("Right Click");
                scroller.rightClick();
            }
            else if(clickElapsed > 200){
                System.out.println("Left Click");
                scroller.singleClick();
            }
        }
        /*if(isThumbExtended() && !mousePress){

        }*/

//
//        System.out.println("Thumb extended:" + isThumbExtended());
//        if (!isThumbExtended() && !isClicking) {
//            isClicking = true;
//            clickStart = System.currentTimeMillis();
//            return;
//        }
//
//        clickElapsed = System.currentTimeMillis() - clickStart;
//        System.out.println("elapsed " + clickElapsed );
//        if (clickElapsed > 4000 && !isThumbExtended() && !isDragging) {
//            isDragging = true;
//            System.out.println("Drag Start");
//            return;
//        }
//
//        if (isThumbExtended()) {
//            if (isDragging) {
//                isDragging = false;
//                isClicking = false;
//
//                System.out.println("Drag End");
//                return;
//            }
//            if (clickElapsed > 2000) {
//                System.out.println("Right Click");
//            } else if (clickElapsed > 500) {
//                System.out.println("Left Click");
//            }
//            isClicking = false;
//            return;
//        }
//
//        isClicking = false;
    }


    private float[] scale() {
        float[] diff = new float[2];
        float iX = (float) initialPosition[INDEX_FINGER].x;
        float iY = (float) initialPosition[INDEX_FINGER].y;
        float cX = (float) currentPosition[INDEX_FINGER].x;
        float cY = (float) currentPosition[INDEX_FINGER].y;

        //float diffX = (float)(initialPosition[INDEX_FINGER].x)-(float)(currentPosition[INDEX_FINGER].x);
        //float diffY =(float)(initialPosition[INDEX_FINGER].y)-(float)(currentPosition[INDEX_FINGER].y);

        diff[0] = (cX - iX) * tempMain.scale[0];
        diff[1] = (cY - iY) * tempMain.scale[1];

        return diff;
    }

    private void reduceFingerTips(Mat frame) {
        ArrayList<Point> fingerPoints = new ArrayList<>();
        float MIN_FINGER_DEPTH = 10;
        float MAX_FINGER_ANGLE = 60;
        int numOfPoints = startPoints.size();
        for (int i = 0; i < numOfPoints; i++) {
//            System.out.println("depth " + i + ": " + depths.get(i));
            if (depths.get(i) < MIN_FINGER_DEPTH) continue;

            int prevIndex = (i == 0) ? (numOfPoints - 1) : (i - 1);
            int nextIndex = (i == numOfPoints - 1) ? 0 : (i + 1);
            int angle = angleBetween(startPoints.get(i), valleyPoints.get(prevIndex), valleyPoints.get(nextIndex));
//            System.out.println("angle " + i + ": " + angle);

            if (angle > MAX_FINGER_ANGLE) continue;

            fingerPoints.add(startPoints.get(i));
//            Imgproc.circle(frame, startPoints.get(i), 10, new Scalar(0, 255, 0), 2);

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
