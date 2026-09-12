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
  /// What the manager answers to a state message: whether the project needs compiling,
  /// the running main if any, and the known mains each with the file declaring it.
  public record State(boolean needsCompiling, String running, Map<String,String> mains){}
  public State state(String alias, Path folder){
    var reply= reports(alias).resolve("state.txt");
    try{ Files.deleteIfExists(reply); }
    catch(IOException e){ throw new UncheckedIOException(e); }
    send("state", folder, reply.toString());
    for (int i= 0; i < 100 && !Files.exists(reply); i++){ pause(); }
    var needsCompiling= false;
    var running= "";
    var mains= new LinkedHashMap<String,String>();
    for (var line : read(reply).lines().toList()){
      var words= line.split(" ");
      if (words[0].equals("needsCompiling")){ needsCompiling= true; }
      if (words[0].equals("running")){ running= words[1]; }
      if (words[0].equals("main")){ mains.put(words[1], words[2]); }
    }
    return new State(needsCompiling, running, mains);
  }
  private static void pause(){
    try{ Thread.sleep(5); }
    catch(InterruptedException e){ Thread.currentThread().interrupt(); }
  }
  public void send(String verb, Path folder){ send(verb+"\n"+folder); }
  public void send(String verb, Path folder, String third){ send(verb+"\n"+folder+"\n"+third); }
  private void send(String message){
    var name= "%020d-%s".formatted(System.currentTimeMillis(), UUID.randomUUID());
    var tmp= messages.resolve(name+".tmp");
    try{
      Files.writeString(tmp, message);
      Files.move(tmp, messages.resolve(name+".msg"), StandardCopyOption.ATOMIC_MOVE);
    }
    catch(IOException e){ throw new UncheckedIOException(e); }
  }
  public static String read(Path file){
    try{ return Files.exists(file) ? Files.readString(file) : ""; }
    catch(IOException e){ throw new UncheckedIOException(e); }
  }
}
