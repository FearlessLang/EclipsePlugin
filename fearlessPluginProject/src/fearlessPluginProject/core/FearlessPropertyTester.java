package fearlessPluginProject.core;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IEditorInput;
import org.eclipse.core.resources.IFile;

public final class FearlessPropertyTester extends PropertyTester{
  @Override public boolean test(Object receiver,String property,Object[] args,Object expected){
    if(!"isFearless".equals(property)){ return false; }
    return isFearless(receiver);
  }
  private static boolean isFearless(Object o){
    var r= asResource(o);
    if (r!=null){ return FearlessProjects.isFearlessResource(r); }
    if(!(o instanceof IEditorInput ei)){ return false; }
    return FearlessProjects.isFearlessResource(ei.getAdapter(IFile.class));
  }
  private static IResource asResource(Object o){
    if(o instanceof IResource r){ return r; }
    if(o instanceof IAdaptable a){ return a.getAdapter(IResource.class); }
    return null;
  }
}