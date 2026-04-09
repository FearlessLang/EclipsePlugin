package fearlessPluginProject.launch;

import java.util.ArrayList;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;

import fearlessPluginProject.core.FearlessProjects;
//TODO: Unreviewd AI
public final class FearlessLaunchDelegate implements org.eclipse.debug.core.model.ILaunchConfigurationDelegate{
  private static final String pluginId= "fearlessPluginProject";

  @Override public void launch(ILaunchConfiguration c,String mode,ILaunch launch,IProgressMonitor m) throws CoreException{
    var projectName= c.getAttribute(FearlessLaunchConfig.attrProject,"");
    if (projectName.isEmpty()){ throw err("Missing project attribute"); }
    var p= ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
    if (!p.isAccessible()){ throw err("Project not accessible: "+projectName); }
    if (!FearlessProjects.isFearlessProject(p)){ throw err("Not a Fearless project: "+projectName); }
    if (p.getLocation() == null){ throw err("Project has no filesystem location: "+projectName); }

    var fearlessExe= FearlessConfig.fearlessExeOrNull();
    if (fearlessExe == null){
      forgetLaunch(launch);
      return;
    }

    cleanOldFearlessLaunchesOrThrowIfRunning(launch);

    var con= console("Fearless");
    showConsole(con);
    showDebugView();

    var proc= new FearlessProcess(launch,"Fearless - "+p.getName(),con,p.getLocation().toFile().toPath(),fearlessExe);
    launch.addProcess(proc);
    DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[]{ new DebugEvent(proc,DebugEvent.CREATE) });
    try{ proc.start(); }
    catch(CoreException e){
      proc.startFailed();
      throw e;
    }
  }

  private static void forgetLaunch(ILaunch launch){
    DebugPlugin.getDefault().getLaunchManager().removeLaunch(launch);
  }

  private static void showDebugView(){
    Display.getDefault().asyncExec(() -> {
      try{
        IWorkbenchWindow w= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (w == null){ return; }
        IWorkbenchPage page= w.getActivePage();
        if (page == null){ return; }
        page.showView(org.eclipse.debug.ui.IDebugUIConstants.ID_DEBUG_VIEW);
      }
      catch(PartInitException ignored){}
    });
  }

  private static void cleanOldFearlessLaunchesOrThrowIfRunning(ILaunch current) throws CoreException{
    var mgr= DebugPlugin.getDefault().getLaunchManager();
    var dead= new ArrayList<ILaunch>();
    for (var l: mgr.getLaunches()){
      if (l == current){ continue; }

      var lc= l.getLaunchConfiguration();
      if (lc == null){ continue; }
      try{
        if (!FearlessLaunchConfig.typeId.equals(lc.getType().getIdentifier())){ continue; }
      }
      catch(CoreException e){ continue; }

      if (l.getProcesses().length == 0){
        dead.add(l);
        continue;
      }
      if (!l.isTerminated()){ throw err("Fearless already running"); }
      dead.add(l);
    }
    if (!dead.isEmpty()){ mgr.removeLaunches(dead.toArray(ILaunch[]::new)); }
  }

  private static CoreException err(String msg){
    return new CoreException(new Status(IStatus.ERROR,pluginId,msg));
  }

  private static MessageConsole console(String name){
    var mgr= ConsolePlugin.getDefault().getConsoleManager();
    for (var c: mgr.getConsoles()){
      if (c instanceof MessageConsole mc && name.equals(mc.getName())){
        mgr.removeConsoles(new IConsole[]{ mc });
        break;
      }
    }
    var mc= new MessageConsole(name,null);
    mgr.addConsoles(new IConsole[]{ mc });
    return mc;
  }

  private static void showConsole(MessageConsole con){
    Display.getDefault().asyncExec(() -> {
      try{
        var w= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (w == null){ return; }
        var page= w.getActivePage();
        if (page == null){ return; }
        var v= (IConsoleView)page.showView(IConsoleConstants.ID_CONSOLE_VIEW);
        v.display(con);
      }
      catch(PartInitException ignored){}
    });
  }
}