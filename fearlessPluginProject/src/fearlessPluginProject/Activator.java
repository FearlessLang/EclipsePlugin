package fearlessPluginProject;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin{
  private FearlessWatcher watcher;
  @Override public void start(BundleContext context) throws Exception{
    super.start(context);
    watcher= new FearlessWatcher();
    watcher.schedule(1000);
  }
  @Override public void stop(BundleContext context) throws Exception{
    if (watcher != null){ watcher.cancel(); }
    super.stop(context);
  }
}
