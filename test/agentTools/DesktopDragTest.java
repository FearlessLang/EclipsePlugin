package agentTools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Desktop;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import agentTools.Pilot.Key;
import tools.Fs;
import utils.OneOr;

/// Drives the real desktop: two folder windows are opened, dragged side by side, and a file is dragged from one to the other.
final class DesktopDragTest{
  private static final Path desktop= Path.of(System.getProperty("user.home"),"Desktop");
  private static final Path source= desktop.resolve("test_file_source");
  private static final Path destination= desktop.resolve("test_file_destination");
  private static final String content= "dragged across the desktop\n";
  @Test void aFileDraggedBetweenTwoFolderWindowsMovesThere(){
    Fs.ensureDir(source);
    Fs.ensureDir(destination);
    Fs.writeUtf8(source.resolve("example.txt"),content);
    var pilot= new Pilot();
    var screen= pilot.shot();
    int w= screen.getWidth(), h= screen.getHeight();
    var src= place(pilot,source,w*5/100,h*5/100,w*48/100,h*90/100);
    var dst= place(pilot,destination,w*52/100,h*5/100,w*95/100,h*90/100);
    var item= item(pilot,src);
    pilot.drag(centerX(item),centerY(item),centerX(dst),centerY(dst));
    Pilot.pause(2000);
    close(pilot,src);
    close(pilot,dst);
    var landed= OneOr.of("the dragged file",Fs.walk(desktop,s->s.filter(this::isTheFile).toList()).stream());
    Fs.ofV(()->Files.delete(landed));
    Fs.rmTree(source);
    Fs.rmTree(destination);
    assertEquals(destination.resolve("example.txt"),landed);
  }
  private boolean isTheFile(Path p){ return p.getFileName().toString().equals("example.txt") && Fs.readUtf8(p).equals(content); }
  private static Rectangle place(Pilot pilot, Path folder, int x0, int y0, int x1, int y1){
    var before= pilot.shot();
    Fs.ofV(()->Desktop.getDesktop().open(folder.toFile()));
    Pilot.pause(3000);
    var win= Pilot.changed(before,pilot.shot(),6);
    assert win.width<before.getWidth() && win.height<before.getHeight();
    pilot.drag(win.x+90,win.y+20,x0+90,y0+20);
    pilot.drag(x0+win.width-1,y0+win.height-1,x1,y1);
    return new Rectangle(x0,y0,x1-x0,y1-y0);
  }
  private static Rectangle item(Pilot pilot, Rectangle win){
    pilot.click(centerX(win),centerY(win));
    var top= new Rectangle(win.x,win.y,win.width,win.height/2);
    var before= crop(pilot.shot(),top);
    pilot.chord(Key.control,Key.a);
    var res= Pilot.changed(before,crop(pilot.shot(),top),0);
    res.translate(win.x,win.y);
    return res;
  }
  private static void close(Pilot pilot, Rectangle win){
    pilot.click(win.x+90,win.y+20);
    pilot.chord(Key.alt,Key.f4);
  }
  private static BufferedImage crop(BufferedImage img, Rectangle r){ return img.getSubimage(r.x,r.y,r.width,r.height); }
  private static int centerX(Rectangle r){ return r.x+r.width/2; }
  private static int centerY(Rectangle r){ return r.y+r.height/2; }
}
