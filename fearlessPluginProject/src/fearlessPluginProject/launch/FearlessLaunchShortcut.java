package fearlessPluginProject.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.ILaunchShortcut2;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import fearlessPluginProject.core.FearlessProjects;

public final class FearlessLaunchShortcut implements ILaunchShortcut2{
  @Override public void launch(ISelection selection, String mode){
    var p= FearlessProjects.projectFrom(selection);
    if (p == null){ return; }
    DebugUITools.launch(cfg(p), mode);
  }
  @Override public void launch(IEditorPart editor,String mode){
    var in= editor.getEditorInput();
    var f= in.getAdapter(IFile.class);
    if (f == null){ return; }
    var p= f.getProject();
    if (!FearlessProjects.isFearlessProject(p)){ return; }
    DebugUITools.launch(cfg(p), mode);
  }
  @Override public ILaunchConfiguration[] getLaunchConfigurations(ISelection selection){
    var p= FearlessProjects.projectFrom(selection);
    if (p == null){ return null; }
    return new ILaunchConfiguration[]{ cfg(p) };
  }
  @Override public ILaunchConfiguration[] getLaunchConfigurations(IEditorPart editor){
    var in= editor.getEditorInput();
    var f= in.getAdapter(IFile.class);
    if (f == null){ return null; }
    var p= f.getProject();
    if (!FearlessProjects.isFearlessProject(p)){ return null; }
    return new ILaunchConfiguration[]{ cfg(p) };
  }
  @Override public IResource getLaunchableResource(ISelection selection){ return FearlessProjects.resourceFrom(selection); }
  @Override public IResource getLaunchableResource(IEditorPart editor){ return editor.getEditorInput().getAdapter(IFile.class); }
  private static ILaunchConfiguration cfg(IProject p){
    try{
      var mgr= DebugPlugin.getDefault().getLaunchManager();
      var type= mgr.getLaunchConfigurationType(FearlessLaunchConfig.typeId);
      for (var c: mgr.getLaunchConfigurations(type)){
        if (p.getName().equals(c.getAttribute(FearlessLaunchConfig.attrProject, ""))){ return c; }
      }
      var wc= type.newInstance(null, mgr.generateLaunchConfigurationName("Fearless - "+p.getName()));
      wc.setAttribute(FearlessLaunchConfig.attrProject, p.getName());
      return wc.doSave();
    }
    catch(CoreException e){ throw new RuntimeException(e); }
  }
}