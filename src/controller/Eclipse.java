package controller;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import controller.Registry.Entry;
import tools.Fs;
import tools.JavacTool;
import userMessages.Report;
import userMessages.Violation;

/// The manager's side of the Eclipse plugin (fearlessPluginProject). Eclipse writes
/// nothing into a project folder: it reads projects.txt and each alias's reports from
/// dir, and asks for work through the messages folder named in manager.txt.
public record Eclipse(Path dir){
  private static final Pattern at= Pattern.compile("(?m)^In file: fear:/(\\S+)\\n\\n(\\d+)\\| ");
  public Path reports(String alias){ return dir.resolve(alias); }
  public void note(String text){ append(dir.resolve("console.txt"),text); }
  public static void append(Path file, String text){
    Fs.ensureDir(file.getParent());
    Fs.ofV(()->Files.writeString(file,text,CREATE,APPEND));
  }
  public String connect(Path chosen, Path msgDir){
    var eclipse= chosen.getParent();
    if (!Files.isRegularFile(eclipse.resolve(".eclipseproduct"))){ throw Report.notAnEclipseInstall(eclipse); }
    var plugin= JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("eclipsePlugin");
    var fearless= eclipse.resolve("dropins").resolve("fearless");
    Fs.copyFresh(plugin,fearless.resolve("plugins"));
    Fs.writeUtf8(fearless.resolve("manager.txt"),msgDir+"\n"+dir+"\n");
    return """
Eclipse is now connected:
%s

Restart Eclipse: every project this manager knows appears in its workspace,
and the Fearless menu offers New project, Run and Terminate.
""".formatted(eclipse);
  }
  public void publish(List<Entry> known){
    var tmp= dir.resolve("projects.tmp");
    Fs.writeUtf8(tmp,String.join("",known.stream().map(e->e.alias()+" "+e.path()+"\n").toList()));
    Fs.ofV(()->Files.move(tmp,dir.resolve("projects.txt"),ATOMIC_MOVE));
  }
  /// One Problems view marker per project: relative file path, line number, then the message; empty when the compile succeeded.
  public static void problems(Path reports, String message){
    var m= at.matcher(message);
    Fs.writeUtf8(reports.resolve("problems.txt"),m.find() ? m.group(1)+"\n"+m.group(2)+"\n"+message : "");
  }
}