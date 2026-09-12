package gui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// The folders a desktop drags into the window: a file list, or the text/uri-list some desktops offer instead.
public final class Drop{
  private Drop(){}
  public static boolean hasFiles(Transferable t){ return t.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || uriList(t).isPresent(); }
  @SuppressWarnings("unchecked")
  public static List<Path> paths(Transferable t){
    try{
      if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)){ return ((List<File>)t.getTransferData(DataFlavor.javaFileListFlavor)).stream().map(File::toPath).toList(); }
      var flavor= uriList(t);
      return flavor.isEmpty() ? List.of() : fromUriList(text(t.getTransferData(flavor.get())));
    }
    catch(UnsupportedFlavorException|IOException e){ return List.of(); }
  }
  private static Optional<DataFlavor> uriList(Transferable t){
    return Stream.of(t.getTransferDataFlavors()).filter(f->f.getPrimaryType().equalsIgnoreCase("text") && f.getSubType().equalsIgnoreCase("uri-list")).findFirst();
  }
  private static String text(Object data) throws IOException{
    if (data instanceof String s){ return s; }
    var r= data instanceof Reader reader ? reader : new InputStreamReader((InputStream)data,StandardCharsets.UTF_8);
    var out= new StringWriter();
    try(r){ r.transferTo(out); }
    return out.toString();
  }
  public static List<Path> fromUriList(String data){
    return data.lines().filter(l->!l.isBlank() && !l.startsWith("#")).flatMap(Drop::path).toList();
  }
  private static Stream<Path> path(String line){
    try{
      var p= Path.of(new URI(line.strip()));
      return p.isAbsolute() ? Stream.of(p) : Stream.of();
    }
    catch(URISyntaxException|IllegalArgumentException e){ return Stream.of(); }
  }
}