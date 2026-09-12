package agentTools;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

import utils.Bug;

/// The desk through java.awt.Robot: windows, mac and X11.
final class Awt implements Desk{
  private final Robot robot= robot();
  private static Robot robot(){
    try{ return new Robot(); }
    catch(AWTException e){ throw Bug.of(e); }
  }
  @Override public void move(int x, int y){ robot.mouseMove(x,y); }
  @Override public void button(Button b, boolean down){ if (down){ robot.mousePress(b.mask); } else { robot.mouseRelease(b.mask); } }
  @Override public void key(Key k, boolean down){ if (down){ robot.keyPress(k.code); } else { robot.keyRelease(k.code); } }
  @Override public BufferedImage shot(){ return robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())); }
  @Override public void close(){}
}
