package camouse;

import java.awt.Dimension;
import java.awt.Toolkit;

public class TempMain {
	//index
	float iX, iY;
	
	//thumb
	float tX, tY;
	
	//center of gravity
	float cX, cY;
	
	
	final long DRAG = 3*1000000000; //3 seconds 
	final long RIGHT = DRAG/2; //1.5 seconds 
	final long LEFT = RIGHT/2; //0.75 seconds 
	
	int prevState;
	int curState;
	
	long prevStateTime;
	long curStateTime;
	ScrollEventTest dangerMouse;

	
	//[0] = x, [1] = y
	float[] screenRez, camRez, scale, negligible, init;
	
	
	public TempMain(float x, float y, float camX, float camY) {
		
		iX = iY = 0;
		tX = tY = 0;
		cX = cY = 0;
		
		screenRez = camRez = scale = negligible = init = new float[2];
		
		dangerMouse = new ScrollEventTest(x, y);
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		
		init[0] = x;
		init[1] = y;
		screenRez[0] = (float) screenSize.getWidth(); 
		screenRez[1] = (float) screenSize.getHeight();
		camRez[0] = camX;
		camRez[1] = camY;
		scale[0] = screenRez[0]/camRez[0];
		scale[1] = screenRez[1]/camRez[1];
		
		negligible[0] = camRez[0]/100;
		negligible[0] = camRez[0]/100;
		
		curState = 2;
		prevState = 0;
		
		prevStateTime = 0;
		curStateTime = System.nanoTime();
	}
	
	//drag
	public void dragBy(float curX, float curY) {
		if (curState == 1) {
			if ((curStateTime - prevStateTime) >= DRAG) {
				//click and hold 
				dangerMouse.leftClickPress();
				//move mouse by 
				float[] moveByThis = moveBy(curX, curY);
				dangerMouse.mouseMovement(moveByThis[0], moveByThis[1]);
			}
		}
	}
	
	public float[] moveBy(float curX, float curY) {
		float[] diff = new float[2];
		diff[0] = (curX - init[0]) * scale[0];
		diff[1] = (curX - init[1]) * scale[1];
		
		return diff;
	}
	
	/*//right click 
	public void detectRightClick() {
		if (curState == 1) {
			if ((curStateTime - prevStateTime) <= RIGHT) {
				//right click
				dangerMouse.rightClick();
			}
		}
	}*/
	
	//drag
	public void scrollBy(float cX, float cY) {
		if (curState == 0) {
			if ((cY - init[1]) < 0) {
				dangerMouse.scrollUp();
			}
			else {
				dangerMouse.scrollDown();
			}
		}
	}
	
	//left click 
	public void click(float curX, float curY) {
		if ((curState == 2) && (prevState == 1)) {
			if((curStateTime - prevStateTime) <= LEFT) { 		//left click
				dangerMouse.singleClick();
			}else if((curStateTime - prevStateTime) <= RIGHT){ 	//right click
				dangerMouse.rightClick();
			}
			else {
				dragBy(curX, curY);
			}
			
		}
	}
	
	public void update(float curX, float curY) {
		boolean tCheck, iCheck, cCheck;
		tCheck = (tX == 0) && (tY == 0);		//thumb check
		iCheck = (iX == 0) && (iY == 0);		//index check
		cCheck = (cX == 0) && (cY == 0);		//Center of Gravity check
		
		prevState = curState;		
		prevStateTime = curStateTime;
		
		if (tCheck && !iCheck) { //thumb is not visible
			curState = 1;			//click/drag mode
			curStateTime = System.nanoTime();
			
			tX = tY = cX = cY = 0;
			iX = curX;
			iY = curY;
			
			click(curX, curY);
		}
		else if (tCheck && iCheck){ //index and thumb are not visible
			curState = 0;			//scroll mode
			curStateTime = System.nanoTime();
			
		}
		else if (tCheck && iCheck && cCheck) { //hand is not visible
			curState = -1;			//calibration mode
			curStateTime = System.nanoTime();
		}
		else {
			curState = 2;			//pointer mode
			curStateTime = System.nanoTime();
			moveBy(curX, curY);

			iX = curX;
			iY = curY;
		}
		
		//update tX/Y, iX/Y, cX/Y
 	}

}
