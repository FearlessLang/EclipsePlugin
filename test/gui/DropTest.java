package gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class DropTest{
  @Test void aFileListDropIsReadStraightFromTheFlavor(){
    var t= fixed(DataFlavor.javaFileListFlavor,List.of(new File("/home/me/myproject")));
    assertTrue(Drop.hasFiles(t));
    assertEquals(List.of(Path.of("/home/me/myproject")),Drop.paths(t));
  }
  @Test void aUriListStringIsSplitOnLines(){
    var t= fixed(uriList("java.lang.String"),"file:///home/me/myproject\r\nfile:///home/me/other.fearless\r\n");
    assertUris(Drop.paths(t),"file:///home/me/myproject","file:///home/me/other.fearless");
  }
  @Test void aUriListReaderIsReadInFull(){
    var t= fixed(uriList("java.io.Reader"),new StringReader("file:///home/me/myproject\n"));
    assertUris(Drop.paths(t),"file:///home/me/myproject");
  }
  @Test void aUriListInputStreamIsReadAsUtf8(){
    var t= fixed(uriList("java.io.InputStream"),new ByteArrayInputStream("file:///home/me/myproject\n".getBytes(StandardCharsets.UTF_8)));
    assertUris(Drop.paths(t),"file:///home/me/myproject");
  }
  @Test void commentAndBlankLinesAreSkipped(){
    assertUris(Drop.fromUriList("# a comment\r\n\r\nfile:///home/me/myproject\r\n"),"file:///home/me/myproject");
  }
  @Test void aSpaceInTheNameIsPercentDecoded(){
    assertUris(Drop.fromUriList("file:///home/me/My%20Project\r\n"),"file:///home/me/My%20Project");
  }
  @Test void aBareLineFeedIsAcceptedToo(){
    assertUris(Drop.fromUriList("file:///home/me/myproject\n"),"file:///home/me/myproject");
  }
  @Test void aDropWithNeitherFlavorHasNothingToOffer(){
    var t= fixed(DataFlavor.stringFlavor,"just text");
    assertFalse(Drop.hasFiles(t));
    assertEquals(List.of(),Drop.paths(t));
  }
  private static DataFlavor uriList(String repClass){
    try{ return new DataFlavor("text/uri-list;class="+repClass); }
    catch(ClassNotFoundException e){ throw new RuntimeException(e); }
  }
  private static void assertUris(List<Path> paths, String... expected){
    assertEquals(List.of(expected),paths.stream().map(Path::toUri).map(Object::toString).toList());
  }
  private static Transferable fixed(DataFlavor flavor, Object data){
    return new Transferable(){
      @Override public DataFlavor[] getTransferDataFlavors(){ return new DataFlavor[]{flavor}; }
      @Override public boolean isDataFlavorSupported(DataFlavor f){ return f.equals(flavor); }
      @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException{
        if (!isDataFlavorSupported(f)){ throw new UnsupportedFlavorException(f); }
        return data;
      }
    };
  }
}