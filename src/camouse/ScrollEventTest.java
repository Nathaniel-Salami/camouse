package camouse;
import java.awt.*;
import java.awt.event.InputEvent;

public class ScrollEventTest {
    private Robot robot;
    //private static Point p;
    private int initialX;
    private int initialY;

    public ScrollEventTest(){
        try {
            this.robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void init(float x, float y){
        this.initialX = (int)x;
        this.initialY = (int)y;
    }

    public void leftClickPress(){
        this.robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
    }

    public void leftClickRelease(){
        this.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public void singleClick(){
        leftClickPress();
        leftClickRelease();
    }

    public void doubleClick(){
        singleClick();
        singleClick();
    }

    public void rightClick(){
        this.robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        this.robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    public void scrollDown(){
        this.robot.mouseWheel(1);
    }

    public void scrollUp(){
        this.robot.mouseWheel(-1);
    }

    public void mouseMovement(float differenceX, float differenceY){
        //p = MouseInfo.getPointerInfo().getLocation();
        //robot.mouseMove(p.x+differenceX, p.y+differenceY);
        this.robot.mouseMove((int)(initialX+differenceX), (int)(initialY+differenceY));
    }
    
}
