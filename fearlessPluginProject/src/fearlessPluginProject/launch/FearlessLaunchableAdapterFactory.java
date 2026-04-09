package fearlessPluginProject.launch;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.debug.ui.actions.ILaunchable;
import fearlessPluginProject.core.FearlessProjects;

public final class FearlessLaunchableAdapterFactory implements IAdapterFactory{
  private static final Class<?>[] adapters= { ILaunchable.class };
  private static final ILaunchable launchable= new ILaunchable(){};

  @Override public Class<?>[] getAdapterList(){ return adapters; }

  @Override public <T> T getAdapter(Object adaptableObject, Class<T> adapterType){
    if (adapterType!=ILaunchable.class){ return null; }
    if (!(adaptableObject instanceof IResource r)){ return null; }
    if (!FearlessProjects.isFearlessResource(r)){ return null; }
    return adapterType.cast(launchable);
  }
}