package fearlessPluginProject;

import java.nio.file.Path;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/// The Fearless menu: every entry is one message to the manager, which does the work.
public final class Commands extends AbstractHandler{
  @Override public Object execute(ExecutionEvent event) throws ExecutionException{
    var link= ManagerLink.find().orElseThrow(()->new ExecutionException("No Fearless manager is connected to this Eclipse."));
    var id= event.getCommand().getId();
    var shell= HandlerUtil.getActiveShell(event);
    if (id.endsWith("newProject")){ newProject(link, shell); return null; }
    var project= currentProject(event);
    var folder= project == null ? null : link.projects().get(project.getName());
    if (folder == null){ MessageDialog.openInformation(shell, "Fearless", "Select a Fearless project first."); return null; }
    link.send(id.endsWith("run") ? "run" : "terminate", folder);
    return null;
  }
  private static void newProject(ManagerLink link, Shell shell){
    var dialog= new DirectoryDialog(shell);
    dialog.setMessage("Choose the folder of the Fearless project");
    var chosen= dialog.open();
    if (chosen != null){ link.send("select", Path.of(chosen)); }
  }
  private static IProject currentProject(ExecutionEvent event){
    var resource= Adapters.adapt(HandlerUtil.getCurrentStructuredSelection(event).getFirstElement(), IResource.class);
    if (resource == null){ resource= Adapters.adapt(HandlerUtil.getActiveEditorInput(event), IResource.class); }
    return resource == null ? null : resource.getProject();
  }
}
