package fearlessPluginProject;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public final class Activator extends AbstractUIPlugin{
  private static Activator instance;
  public static Activator getDefault(){ return instance; }
  @Override public void start(BundleContext ctx) throws Exception{
    super.start(ctx);
    instance= this;
  }
  @Override public void stop(BundleContext ctx) throws Exception{
    instance= null;
    super.stop(ctx);
  }
}