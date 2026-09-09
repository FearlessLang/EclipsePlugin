package fearlessPluginProject;

import org.eclipse.ui.IStartup;

/// Registered on org.eclipse.ui.startup so the platform loads this bundle - and so
/// Activator.start() runs - as soon as the workbench opens, instead of waiting for
/// lazy activation that would otherwise never happen (nothing else in Eclipse ever
/// touches this plugin's classes on its own).
public final class Startup implements IStartup{
  @Override public void earlyStartup(){}
}
