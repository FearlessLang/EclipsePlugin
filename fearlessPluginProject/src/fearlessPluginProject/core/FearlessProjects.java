package fearlessPluginProject.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

public final class FearlessProjects{
  public static boolean isFearlessResource(IResource r){ return r != null && isFearlessProject(r.getProject()); }
  public static boolean isFearlessProject(IProject p){
    if (p==null || !p.isAccessible()) { return false; }
    try{
      for(var m: p.members()){
        if(m.getType() != IResource.FILE) { continue; }
        var ext= m.getFileExtension();
        if("fearless".equals(ext)) { return true; }
      }
      return false;
    }
    catch(Exception e){ return false; }
  }
  public static IProject projectFrom(ISelection sel){
    var r= resourceFrom(sel);
    return FearlessProjects.isFearlessResource(r) ? r.getProject() : null;
  }
  public static IResource resourceFrom(ISelection sel){
    if (!(sel instanceof IStructuredSelection ss)){ return null; }
    var o= ss.getFirstElement();
    if (o instanceof IResource r){ return r; }
    if (o instanceof IAdaptable a){ return a.getAdapter(IResource.class); }
    return null;
  }

}