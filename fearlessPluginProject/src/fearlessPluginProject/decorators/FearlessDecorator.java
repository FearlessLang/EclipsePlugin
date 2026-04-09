package fearlessPluginProject.decorators;

import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.BaseLabelProvider;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.swt.graphics.Image;
import fearlessPluginProject.icons.IconSelector;
public final class FearlessDecorator extends BaseLabelProvider implements ILabelDecorator{
  @Override public Image decorateImage(Image image,Object element){
    var r= asResource(element);
    if(r==null){ return null; }
    var chain= pathFromWsRoot(r);
    return switch(r.getType()){
      case IResource.FILE-> IconSelector.iconForFile(chain);
      case IResource.FOLDER-> IconSelector.iconForFolder(chain);
      default-> null;
      };
    }
  @Override public String decorateText(String text,Object element){ return null; }
  private static IResource asResource(Object e){
    if (e instanceof IResource r){ return r; }
    if (e instanceof IAdaptable a){ return a.getAdapter(IResource.class); }
    return null;
  }
  private static List<String> pathFromWsRoot(IResource r){ return List.of(r.getFullPath().segments()); }
}