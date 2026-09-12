package controller;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import core.TName;
import mainCoordinator.MakeDemo;
import realSourceOracle.BuildWithZip;
import realSourceOracle.PathEntry;
import tools.Fs;
import userMessages.UserError;

/// A project is named by the stem of the one `.fearless` marker file in its folder.
public final class Names{
  private Names(){}
  public static final String ext= ".fearless";
  private static final Path anyRoot= Path.of("").toAbsolutePath();
  public static String compactName(Path folder){
    var f= folder.toAbsolutePath().normalize();
    var all= markers(f);
    return all.size() == 1 ? stem(all.getFirst()) : f.getFileName().toString();
  }
  public static String pkgName(String alias){
    var s= alias.replaceFirst("^_+","");
    return TName.isPkgName(s) && !s.equals("base") && !s.equals("rank") ? s : "app_"+s;
  }
  public static boolean isName(String name){
    PathEntry kid;
    try{ kid= new PathEntry(anyRoot,Path.of(name+ext)); }
    catch(InvalidPathException e){ return false; }
    if (kid.local().getNameCount() != 1 || kid.local().isAbsolute() || BuildWithZip.isInvisible(kid)){ return false; }
    try{ BuildWithZip.checkIndividualVisibleSegment(kid); return true; }
    catch(UserError e){ return false; }
  }
  public static Optional<String> markerProblem(Path folder, String alias){
    var all= markers(folder);
    if (all.size() > 1){
      return Optional.of("More than one .fearless marker file was found in\n"+folder+"\nA project folder holds exactly one, and its name is the project name: keep only \""+alias+ext+"\".");
    }
    if (hasMarker(folder,alias)){ return Optional.empty(); }
    return Optional.of("The marker file \""+alias+ext+"\" is missing from\n"+folder+"\nRestore it, or forget and re-add this project folder.");
  }
  public static String makeUnique(Path folder, Set<String> taken){
    var name= compactName(folder);
    var chosen= isName(name) && !taken.contains(name) ? name : free(folder,folder.getFileName().toString(),taken);
    nameAs(folder,chosen);
    return chosen;
  }
  public static String free(Path folder, String wanted, Set<String> taken){
    var base= wanted.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+","_");
    var name= isName(base) ? base : "p"+base;
    return IntStream.iterate(1,i->i+1).mapToObj(i->i == 1 ? name : name+i).filter(n->isFree(folder,n,taken)).findFirst().orElseThrow();
  }
  private static boolean isFree(Path folder, String name, Set<String> taken){ return !taken.contains(name) && !hasMarker(folder,name); }
  private static boolean hasMarker(Path folder, String name){ return Files.exists(folder.resolve(name+ext)); }
  private static void nameAs(Path folder, String name){
    assert isName(name);
    var target= folder.resolve(name+ext);
    if (Files.exists(target)){ return; }
    var all= markers(folder);
    if (all.isEmpty()){ Fs.writeUtf8(target,MakeDemo.markerContent); return; }
    Fs.ofV(()->Files.move(all.getFirst(),target));
  }
  private static List<Path> markers(Path folder){
    if (!Files.isDirectory(folder)){ return List.of(); }
    return Fs.of(()->{ try(var s= Files.list(folder)){ return s
      .filter(Files::isRegularFile)
      .filter(p->p.getFileName().toString().endsWith(ext))
      .sorted()
      .toList();
    }});
  }
  private static String stem(Path file){
    var name= file.getFileName().toString();
    return name.substring(0,name.length()-ext.length());
  }
}