package fearlessPluginProject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.core.runtime.Platform;

/// What the manager left at connect time (see manager.EclipseConnect in Coordinator):
/// manager.txt names its messages folder, then its eclipse folder. In the latter,
/// projects.txt lists every registered project as alias, space, folder, and each
/// alias's reports (problems.txt, report.xml) sit in a folder of that name.
/// A message is a verb, a newline, then a project folder; the manager drains
/// *.msg files, so a message is written as .tmp and renamed into place.
public final class ManagerLink{
  private final Path messages;
  private final Path eclipse;
  private ManagerLink(Path messages, Path eclipse){ this.messages= messages; this.eclipse= eclipse; }
  public static Optional<ManagerLink> find(){
    var install= new File(Platform.getInstallLocation().getURL().getPath());
    var file= new File(install,"dropins/fearless/manager.txt").toPath();
    if (!Files.exists(file)){ return Optional.empty(); }
    var lines= read(file).lines().toList();
    return Optional.of(new ManagerLink(Path.of(lines.get(0)), Path.of(lines.get(1))));
  }
  public Map<String,Path> projects(){
    var res= new LinkedHashMap<String,Path>();
    for (var line : read(eclipse.resolve("projects.txt")).lines().toList()){
      var space= line.indexOf(' ');
      res.put(line.substring(0,space), Path.of(line.substring(space+1)));
    }
    return res;
  }
  public Path reports(String alias){ return eclipse.resolve(alias); }
  public Path console(){ return eclipse.resolve("console.txt"); }
  public void send(String verb, Path folder){
    var name= "%020d-%s".formatted(System.currentTimeMillis(), UUID.randomUUID());
    var tmp= messages.resolve(name+".tmp");
    try{
      Files.writeString(tmp, verb+"\n"+folder);
      Files.move(tmp, messages.resolve(name+".msg"), StandardCopyOption.ATOMIC_MOVE);
    }
    catch(IOException e){ throw new UncheckedIOException(e); }
  }
  public static String read(Path file){
    try{ return Files.exists(file) ? Files.readString(file) : ""; }
    catch(IOException e){ throw new UncheckedIOException(e); }
  }
}
