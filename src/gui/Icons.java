package gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.Icon;

import controller.Association;
import tools.Fs;
import userMessages.Violation;
import utils.Range;

public final class Icons{
  private Icons(){}
  public enum Mark{ none, idle, dataReadOnly, dataReadWrite, attention, invalid, compiled, running }
  private static Image app;
  static Image app(){
    if (app == null){ app= read(Association.iconFile()); }
    return app;
  }
  public static Image folder(Path folder, int size){
    var dir= folder.toAbsolutePath().normalize().resolve(".config").resolve("icon");
    if (!Files.isDirectory(dir)){ return generated(controller.Names.compactName(folder),size); }
    var pngs= Fs.of(()->{ try(var s= Files.list(dir)){ return s
      .filter(Files::isRegularFile)
      .filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
      .sorted()
      .toList();
    }});
    if (pngs.size() > 1){ throw Violation.multipleIcons(dir,pngs); }
    return pngs.isEmpty() ? generated(controller.Names.compactName(folder),size) : read(pngs.getFirst());
  }
  public static Image read(Path file){
    try{
      var res= ImageIO.read(file.toFile());
      if (res == null){ throw Violation.couldNotDecodeIcon(file); }
      return res;
    }
    catch(IOException e){ throw Violation.couldNotLoadIcon(file,e); }
  }
  //Four coloured quarters chosen by the name, under the first two letters of the name.
  static BufferedImage generated(String name, int size){
    var rnd= new Random(name.hashCode());
    var base= rnd.nextFloat();
    var res= new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);
    var g= res.createGraphics();
    var half= size/2;
    var lum= 0.0;
    for (int i : Range.of(0,4)){
      var c= Color.getHSBColor(base+i*0.25f+rnd.nextFloat()*0.1f-0.05f,0.5f+rnd.nextFloat()*0.45f,0.45f+rnd.nextFloat()*0.45f);
      lum+= (0.299*c.getRed()+0.587*c.getGreen()+0.114*c.getBlue())/(4*255);
      g.setColor(c);
      g.fillRect(i%2*half,i/2*half,size-half,size-half);
    }
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,size/2));
    g.setColor(lum > 0.5 ? Color.black : Color.white);
    var text= name.strip().substring(0,Math.min(2,name.strip().length())).toUpperCase(Locale.ROOT);
    var m= g.getFontMetrics();
    g.drawString(text,(size-m.stringWidth(text))/2,(size-m.getHeight())/2+m.getAscent());
    g.dispose();
    return res;
  }
  public record Badge(Image image, int size, Mark mark) implements Icon{
    @Override public int getIconWidth(){ return size; }
    @Override public int getIconHeight(){ return size; }
    @Override public void paintIcon(Component c, Graphics g, int x, int y){
      var g2= (Graphics2D)g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      g2.drawImage(image,x,y,size,size,c);
      switch(mark){
        case none -> {}
        case idle -> dot(g2,x,y,new Color(0x757575),"");
        case dataReadOnly -> dot(g2,x,y,new Color(0x1565C0),"R");
        case dataReadWrite -> dot(g2,x,y,new Color(0x6A1B9A),"W");
        case attention -> dot(g2,x,y,new Color(0xEF6C00),"!");
        case invalid -> dot(g2,x,y,new Color(0xC62828),"X");
        case compiled -> dot(g2,x,y,new Color(0x2E7D32),">");
        case running -> running(g2,x,y);
      }
      g2.dispose();
    }
    private void dot(Graphics2D g, int x, int y, Color color, String glyph){
      var d= Math.max(10,size/3);
      var at= x+size-d;
      var top= y+size-d;
      ring(g,at,top,d);
      g.setColor(color);
      g.fillOval(at,top,d,d);
      g.setColor(Color.white);
      g.setFont(g.getFont().deriveFont(Font.BOLD,d*0.7f));
      var m= g.getFontMetrics();
      g.drawString(glyph,at+(d-m.stringWidth(glyph))/2f,top+(d+m.getAscent()-m.getDescent())/2f);
    }
    //a partial ring drawn at a time-derived angle, so a list that repaints while this project runs shows it spinning
    private void running(Graphics2D g, int x, int y){
      var d= Math.max(8,size/4);
      var at= x+size-d;
      var top= y+size-d;
      ring(g,at,top,d);
      g.setColor(new Color(0x2E7D32));
      g.setStroke(new BasicStroke(Math.max(2f,d/4f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
      g.drawArc(at,top,d,d,(int)(System.currentTimeMillis()/8%360),270);
    }
    private static void ring(Graphics2D g, int at, int top, int d){
      g.setColor(Color.white);
      g.fillOval(at-2,top-2,d+4,d+4);
    }
  }
}