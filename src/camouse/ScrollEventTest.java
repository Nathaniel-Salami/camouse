package camouse;
import java.awt.*;
import java.awt.event.InputEvent;

public class ScrollEventTest {
    private static Robot robot;
    //private static Point p;
    private static int initialX;
    private static int initialY;

    public ScrollEventTest(int x, int y){
        try {
            this.robot = new Robot();  
        } catch (AWTException e) {
            e.printStackTrace();
        }
        
        this.initialX = x;
        this.initialY = y;
    }

    public static void singleClick(){
        this.robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        this.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void doubleClick(){
        singleClick();
        singleClick();
    }

    public static void rightClick(){
        this.robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        this.robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    public static void scrollDown(){
        this.robot.mouseWheel(1);
    }

    public static void scrollUp(){
        this.robot.mouseWheel(-1);
    }

    public static void mouseMovement(int differenceX, int differenceY){
        //p = MouseInfo.getPointerInfo().getLocation();
        //robot.mouseMove(p.x+differenceX, p.y+differenceY);
        this.robot.mouseMove(initialX+differenceX, initialY+differenceY);
    }
    
}
