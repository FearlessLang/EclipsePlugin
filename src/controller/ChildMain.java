package controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.E.Literal;
import core.OtherPackages;
import fileSupport.LogFiles;
import fileSupport.NativeLocaleForcer;
import naiveBackend.BackendTools;
import tools.ChildJvm;
import tools.Fs;
import tools.SourceOracle;
import userMessages.UserError;

/// The child JVM the manager starts to compile one project: args are the project
/// folder and the folder where the problem report for Eclipse goes.
public class ChildMain{
  public static void main(String[] args){
    NativeLocaleForcer.forceEnglish();
    ChildJvm.watchParent();
    var project= Path.of(args[0]);
    var reports= Path.of(args[1]);
    Eclipse.problems(reports,"");
    //Puts .out in place before the compile stamps .fearless_out, so the project
    //root's mtime cannot end up newer than that stamp.
    Fs.ensureDir(project.resolve(LogFiles.runDir));
    var exitCode= 0;
    var problem= "";
    try{ compile(project); }
    catch(UserError e){ exitCode= 1; problem= e.getMessage(); System.err.print(problem); }
    catch(Throwable t){ exitCode= 2; System.err.print(UserError.crash(t)); }
    Eclipse.problems(reports,problem);
    System.out.flush();
    System.err.flush();
    System.exit(exitCode);
  }
  private static void compile(Path project){
    UserError.root= project;
    var c= new Coordinator(){
      @Override public Optional<Path> baseCachePath(){ return Optional.of(Session.stdLib("baseCache")); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName,oracle,other,core,project.resolve(Coordinator.outDir),baseCachePath(),Session.stdLib("rt"),capabilities);
      }
    };
    c.compile(project,c.sourceOracle(Session.stdLib("base")));
  }
}