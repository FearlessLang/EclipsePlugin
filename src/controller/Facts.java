package controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import controller.Registry.Kind;
import coordinator.Coordinator;
import fileSupport.LogFiles;
import realSourceOracle.RealSourceOracleWithZip;
import tools.Fs;
import userMessages.UserError;

/// What the file system says about a project folder: its authored files, and the
/// compiled cache Fearless keeps for it under `.fearless_out`.
public record Facts(Path folder, int files, long bytes, long modified, long jsonStamp, long cacheStamp, List<String> pkgs, Optional<String> problem){
  public static final String outDir= Coordinator.outDir;
  public boolean valid(){ return problem.isEmpty(); }
  public boolean cacheUpToDate(){ return cacheStamp >= 0 && cacheStamp >= modified; }
  public boolean hasCache(){ return hasCache(folder); }
  public static boolean hasCache(Path folder){ return Files.isDirectory(folder.toAbsolutePath().normalize().resolve(outDir)); }
  public static Facts of(Path folder, Kind kind){
    var f= folder.toAbsolutePath().normalize();
    var src= sources(f);
    List<String> pkgs= List.of();
    Optional<String> problem= Optional.empty();
    UserError.root= f;
    try{ if (kind == Kind.code){ pkgs= Coordinator.pkgNames(f); } else { new RealSourceOracleWithZip(f); } }
    catch(UserError e){ problem= Optional.of(e.getMessage()); }
    return new Facts(f,src.size(),src.stream().mapToLong(p->Fs.of(()->Files.size(p))).sum(),modified(f),stamp(f,".json",true),stamp(f,".built",false),pkgs,problem);
  }
  public static long modified(Path folder){
    var f= folder.toAbsolutePath().normalize();
    return Math.max(newest(sources(f)),newest(Fs.walk(f,s->authored(f,s).filter(Files::isDirectory).toList())));
  }
  public static boolean cacheUpToDate(Path folder, long modified){
    var built= stamp(folder.toAbsolutePath().normalize(),".built",false);
    return built >= 0 && built >= modified;
  }
  private static List<Path> sources(Path folder){ return Fs.walk(folder,s->authored(folder,s).filter(Files::isRegularFile).toList()); }
  private static Stream<Path> authored(Path folder, Stream<Path> all){
    var cache= folder.resolve(outDir);
    var written= folder.resolve(LogFiles.runDir);
    return all.filter(p->!p.startsWith(cache) && !p.startsWith(written));
  }
  private static long newest(List<Path> files){ return files.stream().mapToLong(Fs::lastModified).max().orElse(-1); }
  private static long stamp(Path folder, String ext, boolean newest){
    var out= folder.resolve(outDir);
    if (!Files.isDirectory(out)){ return -1; }
    var all= LongStream.of(Fs.walk(out,s->s.filter(p->p.getFileName().toString().endsWith(ext)).mapToLong(Fs::lastModified).toArray()));
    return (newest ? all.max() : all.min()).orElse(-1);
  }
}