package fearlessPluginProject;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/// Polls every open Fearless project for the two files the manager writes on each compile/run
/// (see managerInfo.ProblemReport and managerInfo.JUnitReport in Coordinator) and reflects
/// whatever changed into the Problems view and the JUnit view. No connection handshake beyond
/// "the jars are installed and a matching folder is open as an Eclipse project": the manager
/// and the plugin agree only on where those two files live under a project's own .out folder.
public final class FearlessWatcher extends Job{
  private static final long periodMs= 2000;
  private final Map<IProject,String> lastProblems= new HashMap<>();
  private final Map<IProject,String> lastJUnit= new HashMap<>();
  public FearlessWatcher(){ super("Fearless connect"); setSystem(true); }
  @Override protected IStatus run(IProgressMonitor monitor){
    if (monitor.isCanceled()){ return Status.CANCEL_STATUS; }
    for (var project : ResourcesPlugin.getWorkspace().getRoot().getProjects()){
      if (project.isOpen() && FearlessProjects.isFearlessProject(project)){ tick(project); }
    }
    schedule(periodMs);
    return Status.OK_STATUS;
  }
  private void tick(IProject project){
    var location= project.getLocation();
    if (location == null){ return; }
    var root= java.nio.file.Path.of(location.toOSString());
    tickProblems(project, root);
    tickJUnit(project, root);
  }
  private void tickProblems(IProject project, java.nio.file.Path root){
    var text= readOrEmpty(root.resolve(".out").resolve("eclipse").resolve("problems.txt"));
    if (text.equals(lastProblems.get(project))){ return; }
    lastProblems.put(project, text);
    ProblemMarkers.apply(project, text);
  }
  private void tickJUnit(IProject project, java.nio.file.Path root){
    var f= root.resolve(".out").resolve("eclipse").resolve("junit_xml").resolve("report.xml");
    var text= readOrEmpty(f);
    if (text.isBlank() || text.equals(lastJUnit.get(project))){ return; }
    lastJUnit.put(project, text);
    JUnitImport.doImport(f);
  }
  private static String readOrEmpty(java.nio.file.Path f){
    try{ return Files.exists(f) ? Files.readString(f) : ""; }
    catch(IOException e){ return ""; }
  }
}
