package fearlessPluginProject;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

/// Every two seconds, mirrors the manager's registered projects into the workspace and
/// reflects their reports into the Problems view and the JUnit view - but only while the
/// Fearless perspective (FearlessPerspective.id) is the active perspective of the active
/// window; otherwise every project this watcher ever mirrored is treated as forgotten and
/// removed, exactly like a project the manager itself stopped registering.
/// A mirrored project lives in the workspace's own folder and reaches the real project
/// folder only through a linked folder, so Eclipse never writes into that folder.
/// Mirrored projects are marked with a persistent property: only those are ever
/// deleted (from the workspace, never from disk) once the manager forgets them.
public final class FearlessWatcher extends Job{
  private static final long periodMs= 2000;
  static final String srcName= "src";
  private static final QualifiedName mirrored= new QualifiedName("fearlessPluginProject","mirrored");
  private final Map<String,String> lastProblems= new HashMap<>();
  private final Map<String,String> lastJUnit= new HashMap<>();
  private final Map<String,Integer> shownConsole= new HashMap<>();
  public FearlessWatcher(){
    super("Fearless connect");
    setSystem(true);
    setRule(ResourcesPlugin.getWorkspace().getRoot());
  }
  @Override protected IStatus run(IProgressMonitor monitor){
    if (monitor.isCanceled()){ return Status.CANCEL_STATUS; }
    var link= ManagerLink.find();
    try{ if (link.isPresent()){ tick(link.get(), monitor); } }
    catch(CoreException e){ return e.getStatus(); }
    schedule(periodMs);
    return Status.OK_STATUS;
  }
  private void tick(ManagerLink link, IProgressMonitor monitor) throws CoreException{
    var active= fearlessActive();
    var projects= active ? link.projects() : Map.<String,java.nio.file.Path>of();
    if (active){ tail("Fearless", link.console()); }
    var root= ResourcesPlugin.getWorkspace().getRoot();
    for (var p : root.getProjects()){
      var forgotten= p.isOpen() && p.getPersistentProperty(mirrored) != null && !projects.containsKey(p.getName());
      if (forgotten){ p.delete(false, true, monitor); }
    }
    for (var e : projects.entrySet()){
      var project= root.getProject(e.getKey());
      mirror(project, e.getValue(), monitor);
      if (project.isOpen() && project.getPersistentProperty(mirrored) != null){ reflect(link, project, monitor); }
    }
  }
  private static boolean fearlessActive(){
    if (!PlatformUI.isWorkbenchRunning()){ return false; }
    var workbench= PlatformUI.getWorkbench();
    var result= new boolean[1];
    workbench.getDisplay().syncExec(()->result[0]= isFearlessPerspective(workbench));
    return result[0];
  }
  private static boolean isFearlessPerspective(IWorkbench workbench){
    var window= workbench.getActiveWorkbenchWindow();
    var page= window == null ? null : window.getActivePage();
    var perspective= page == null ? null : page.getPerspective();
    return perspective != null && perspective.getId().equals(FearlessPerspective.id);
  }
  private static void mirror(IProject project, java.nio.file.Path folder, IProgressMonitor monitor) throws CoreException{
    if (!project.exists()){
      project.create(monitor);
      project.open(monitor);
      project.setPersistentProperty(mirrored, "yes");
    }
    if (!project.isOpen() || project.getPersistentProperty(mirrored) == null){ return; }
    var src= project.getFolder(srcName);
    var location= new Path(folder.toString());
    if (src.exists() && location.equals(src.getLocation())){ return; }
    if (src.exists()){ src.delete(IResource.NONE, monitor); }
    src.createLink(location, IResource.NONE, monitor);
  }
  private void tail(String name, java.nio.file.Path file){
    var text= ManagerLink.read(file);
    var shown= shownConsole.getOrDefault(name, 0);
    if (text.length() < shown){ shown= 0; }
    if (text.length() > shown){ Consoles.print(name, text.substring(shown), shown == 0); }
    shownConsole.put(name, text.length());
  }
  private void reflect(ManagerLink link, IProject project, IProgressMonitor monitor) throws CoreException{
    var alias= project.getName();
    var problems= ManagerLink.read(link.reports(alias).resolve("problems.txt"));
    if (!problems.equals(lastProblems.get(alias))){
      lastProblems.put(alias, problems);
      project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
      ProblemMarkers.apply(project.getFolder(srcName), problems);
    }
    tail("Fearless "+alias, link.reports(alias).resolve("console.txt"));
    var report= link.reports(alias).resolve("report.xml");
    var xml= ManagerLink.read(report);
    if (xml.isBlank() || xml.equals(lastJUnit.get(alias))){ return; }
    lastJUnit.put(alias, xml);
    JUnitImport.doImport(report);
  }
}
