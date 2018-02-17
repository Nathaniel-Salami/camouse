import java.awt.*;
import java.awt.event.InputEvent;

public class ScrollEventTest {
    private static Robot robot;
    private static Point p;

    public static void main(String[] args){
        {
            try {
                robot = new Robot();
                System.out.println("Here");
            } catch (AWTException e) {
                System.out.println("Error");
                e.printStackTrace();
            }
        }
        robot.delay(5000);
        mouseMovement(20,20);
    }

    public static void singleClick(){
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void doubleClick(){
        singleClick();
        singleClick();
    }

    public static void rightClick(){
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    public static void scrollDown(){
        robot.delay(1000);
        robot.mouseWheel(1);
    }

    public static void scrollUp(){
        robot.mouseWheel(-1);
    }

    public static void mouseMovement(int differenceX, int differenceY){
        p = MouseInfo.getPointerInfo().getLocation();
        robot.mouseMove(p.x+differenceX, p.y+differenceY);
    }
}
