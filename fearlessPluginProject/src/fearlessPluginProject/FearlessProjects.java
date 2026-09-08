package fearlessPluginProject;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

/// A Fearless project is any Eclipse project whose root contains a *.fearless file
/// (the manager's own project marker - see manager.FileAssociation in Coordinator).
public final class FearlessProjects{
  private FearlessProjects(){}
  public static boolean isFearlessProject(IProject p){
    if (p == null || !p.isAccessible()){ return false; }
    try{
      for (var m : p.members()){
        if (m.getType() != IResource.FILE){ continue; }
        if ("fearless".equals(m.getFileExtension())){ return true; }
      }
      return false;
    }
    catch(Exception e){ return false; }
  }
}
