package fearlessPluginProject.launch;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

final class _OLD_FearlessReflectiveRunner{
  public static final Path jarsDir= Path.of("C:\\Users\\Lardo\\OneDrive\\Documents\\GitHub\\StandardLibrary\\fearlessArtefact\\fearless\\app\\mods");
  public static final Path baseDir= Path.of("C:\\Users\\Lardo\\OneDrive\\Documents\\GitHub\\StandardLibrary\\fearlessArtefact\\fearless\\app\\stdLib\\base");
  public static final Path rtDir= Path.of("C:\\Users\\Lardo\\OneDrive\\Documents\\GitHub\\StandardLibrary\\fearlessArtefact\\fearless\\app\\stdLib\\rt");
  private static volatile URLClassLoader cl;
  private static volatile Method runMethod;
  public static void load(){
    if(cl != null){ return; }
    synchronized(_OLD_FearlessReflectiveRunner.class){
      if(cl != null){ return; }
      Path commons= jarsDir.resolve("Commons.jar");
      Path coordinator= jarsDir.resolve("Coordinator.jar");
      Path frontend= jarsDir.resolve("FearlessFrontend.jar");
      URL[] cp= new URL[]{ toUrl(commons), toUrl(coordinator), toUrl(frontend) };
      try{
        cl= new URLClassLoader(cp, ClassLoader.getPlatformClassLoader());
        Class<?> k= cl.loadClass("mainCoordinator.ProgrammaticMain");
        runMethod= k.getMethod("runFearless", Path.class,Path.class,Path.class);
      }
      catch(Exception e){
        if (cl != null){ try{ cl.close(); } catch(Exception ignored){} }
        cl= null;
        runMethod= null;
        throw new RuntimeException(e);
      }
    }
  }
  public static void runFearless(Path pathToProjectRoot) throws InterruptedException{
    load();
    try{ runMethod.invoke(null, pathToProjectRoot,baseDir,rtDir); }
    catch(InvocationTargetException ite){
      Throwable t= ite.getTargetException();
      if (t instanceof InterruptedException ie){ throw ie; }
      if (t instanceof RuntimeException re){ throw re; }
      if (t instanceof Error er){ throw er; }
      throw new RuntimeException(t);
    }
    catch(IllegalAccessException e){ throw new RuntimeException(e); }
  }
  private static URL toUrl(Path p){
    try{ return p.toUri().toURL(); }
    catch(Exception e){ throw new RuntimeException(e); }
  }
}