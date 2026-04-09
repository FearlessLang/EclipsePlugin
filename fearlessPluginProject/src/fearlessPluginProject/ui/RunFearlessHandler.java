package fearlessPluginProject.ui;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import fearlessPluginProject.core.FearlessProjects;
import fearlessPluginProject.launch.FearlessLaunchShortcut;

public final class RunFearlessHandler extends AbstractHandler{//TODO: refactor away
  @Override public Object execute(ExecutionEvent e){
    var p= FearlessProjects.projectFrom(HandlerUtil.getCurrentSelection(e));
    if (p == null){ return null; }
    var sc= new FearlessLaunchShortcut();
    DebugUITools.launch(sc.getLaunchConfigurations(new StructuredSelection(p))[0], ILaunchManager.RUN_MODE);
    return null;
  }
}