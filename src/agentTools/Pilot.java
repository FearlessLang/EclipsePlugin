package agentTools;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Set;

import utils.Bug;

/// Drives the desk the way a person does: the pointer glides, buttons and keys are held and released, the screen is looked at.
public final class Pilot{
  public enum Button{
    left(InputEvent.BUTTON1_DOWN_MASK), middle(InputEvent.BUTTON2_DOWN_MASK), right(InputEvent.BUTTON3_DOWN_MASK);
    final int mask;
    Button(int mask){ this.mask= mask; }
  }
  public enum Key{
    a(KeyEvent.VK_A), control(KeyEvent.VK_CONTROL), alt(KeyEvent.VK_ALT), f4(KeyEvent.VK_F4);
    final int code;
    Key(int code){ this.code= code; }
  }
  public static final Set<Button> none= Set.of();
  public static final Set<Button> left= Set.of(Button.left);
  private final Robot robot= robot();
  private Set<Button> down= none;
  private static Robot robot(){
    try{ return new Robot(); }
    catch(AWTException e){ throw Bug.of(e); }
  }
  /// Puts the pointer at (x0,y0) holding exactly the buttons in held, glides it to (x1,y1) at about a pixel a millisecond, then holds exactly the buttons in then.
  public void glide(int x0, int y0, Set<Button> held, int x1, int y1, Set<Button> then){
    robot.mouseMove(x0,y0);
    hold(held);
    int n= Math.max(Math.abs(x1-x0),Math.abs(y1-y0))/4+1;
    for (int i= 1; i<=n; i++){ robot.mouseMove(x0+(x1-x0)*i/n,y0+(y1-y0)*i/n); pause(4); }
    hold(then);
  }
  private void hold(Set<Button> want){
    for (var b: Button.values()){ if (down.contains(b)!=want.contains(b)){ button(b,want.contains(b)); } }
    down= want;
    pause(200);
  }
  private void button(Button b, boolean press){ if (press){ robot.mousePress(b.mask); } else { robot.mouseRelease(b.mask); } }
  public void click(int x, int y){ glide(x,y,none,x,y,left); glide(x,y,left,x,y,none); }
  public void drag(int x0, int y0, int x1, int y1){ glide(x0,y0,none,x0,y0,left); glide(x0,y0,left,x1,y1,none); }
  public void chord(Key... keys){
    for (var k: keys){ robot.keyPress(k.code); }
    for (int i= keys.length-1; i>=0; i--){ robot.keyRelease(keys[i].code); }
    pause(200);
  }
  public BufferedImage shot(){ return robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())); }
  public static void pause(int millis){
    try{ Thread.sleep(millis); }
    catch(InterruptedException e){ throw new RuntimeException(e); }
  }
  /// The box around the largest connected area that differs between the two shots, counting only pixels whose whole (2r+1) square neighbourhood differs: r=0 finds any change, r=6 ignores changing text and finds a new window.
  public static Rectangle changed(BufferedImage a, BufferedImage b, int r){
    int w= a.getWidth(), h= a.getHeight(), side= 2*r+1;
    assert w==b.getWidth() && h==b.getHeight();
    var pa= a.getRGB(0,0,w,h,null,0,w);
    var pb= b.getRGB(0,0,w,h,null,0,w);
    var sum= new int[(w+1)*(h+1)];
    for (int y= 0; y<h; y++){ for (int x= 0; x<w; x++){ sum[(y+1)*(w+1)+x+1]= (differs(pa[y*w+x],pb[y*w+x])?1:0)+sum[y*(w+1)+x+1]+sum[(y+1)*(w+1)+x]-sum[y*(w+1)+x]; } }
    var solid= new boolean[w*h];
    for (int y= r; y<h-r; y++){ for (int x= r; x<w-r; x++){ solid[y*w+x]= box(sum,w,x-r,y-r,x+r+1,y+r+1)==side*side; } }
    var stack= new int[w*h];
    var best= new Rectangle();
    int bestN= 0;
    for (int i= 0; i<w*h; i++){
      if (!solid[i]){ continue; }
      int n= 0, top= 0, minx= w, maxx= 0, miny= h, maxy= 0;
      stack[top++]= i;
      solid[i]= false;
      while (top>0){
        int p= stack[--top], x= p%w, y= p/w;
        n++;
        minx= Math.min(minx,x); maxx= Math.max(maxx,x); miny= Math.min(miny,y); maxy= Math.max(maxy,y);
        for (int q: new int[]{p-1,p+1,p-w,p+w}){ if (q>=0 && q<w*h && solid[q] && Math.abs(q%w-x)<=1){ solid[q]= false; stack[top++]= q; } }
      }
      if (n>bestN){ bestN= n; best= new Rectangle(minx-r,miny-r,maxx-minx+1+2*r,maxy-miny+1+2*r); }
    }
    return best;
  }
  private static int box(int[] sum, int w, int x0, int y0, int x1, int y1){ return sum[y1*(w+1)+x1]-sum[y0*(w+1)+x1]-sum[y1*(w+1)+x0]+sum[y0*(w+1)+x0]; }
  private static boolean differs(int p, int q){
    return Math.abs(((p>>16)&255)-((q>>16)&255))>10 || Math.abs(((p>>8)&255)-((q>>8)&255))>10 || Math.abs((p&255)-(q&255))>10;
  }
}
