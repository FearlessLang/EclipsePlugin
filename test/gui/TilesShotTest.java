package gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import controller.Registry;
import controller.Registry.Kind;
import tools.Fs;
import utils.Box;
import utils.ThrowingConsumer;

/// Renders the tiles grid off screen and checks it actually draws, without a real display.
final class TilesShotTest{
  private static Registry ten(Path dir){
    var r= new Registry(dir);
    for (var name: new String[]{"someproject","otherproject","map_editor","webshop","hello","sudoku","payroll","tetris","notes","weather"}){
      var project= dir.resolve(name);
      Fs.writeUtf8(project.resolve("readme"),"hi\n");
      r.add(name,project);
      r.update(project,e->e.withKind(Kind.dataReadOnly));
    }
    return r;
  }
  @Test void theRegisteredFoldersLookLikeAGridOfTiles(@TempDir Path dir){
    var tiles= onEdt(()->new Tiles(ten(dir),_->false,_->{}));
    assertTrue(colours(shoot(tiles,760,420)) > 40,"the tiles drew nothing");
  }
  @Test void reorderingKeepsTheTilesDrawn(@TempDir Path dir){
    var tiles= onEdt(()->new Tiles(ten(dir),_->false,_->{}));
    onEdt(()->{ tiles.sortBy(Tiles.Sort.Modified); return null; });
    assertTrue(colours(shoot(tiles,760,420)) > 40);
  }
  @Test void selectingAFolderReportsItThroughTheCallback(@TempDir Path dir){
    var picked= new Box<Optional<Path>>(Optional.empty());
    var tiles= onEdt(()->new Tiles(ten(dir),_->false,picked::set));
    onEdt(()->{ tiles.select(dir.resolve("hello")); return null; });
    assertTrue(picked.get().filter(p->p.equals(dir.resolve("hello").toAbsolutePath().normalize())).isPresent());
  }
  private static BufferedImage shoot(JComponent c, int w, int h){
    return onEdt(()->{
      c.setSize(w,h);
      layout(c);
      var res= new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
      var g= res.createGraphics();
      g.setColor(Color.white);
      g.fillRect(0,0,w,h);
      c.printAll(g);
      g.dispose();
      return res;
    });
  }
  private static void layout(Component c){
    if (!(c instanceof Container p)){ return; }
    p.doLayout();
    for (var kid: p.getComponents()){ layout(kid); }
  }
  private static long colours(BufferedImage img){
    return java.util.Arrays.stream(img.getRGB(0,0,img.getWidth(),img.getHeight(),null,0,img.getWidth())).distinct().count();
  }
  private static <T> T onEdt(Supplier<T> f){
    var out= new Box<T>(null);
    ThrowingConsumer.of(SwingUtilities::invokeAndWait).accept(()->out.set(f.get()));
    return out.get();
  }
}