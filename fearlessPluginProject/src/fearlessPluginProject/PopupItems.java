package fearlessPluginProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CompoundContributionItem;

/// The Fearless entries of the right-click menu on a mirrored project or anything in it:
/// Run for the mains declared in the selected file (all the mains of the project when the
/// selection is not a file declaring one), Compile while the project is not compiled, and
/// Terminate while a main of the project runs. The manager is asked for the state when
/// the menu opens; every entry is one message to it.
public final class PopupItems extends CompoundContributionItem{
  @Override protected IContributionItem[] getContributionItems(){
    var link= ManagerLink.find();
    var resource= selectedResource();
    if (link.isEmpty() || resource == null){ return new IContributionItem[0]; }
    var alias= resource.getProject().getName();
    var folder= link.get().projects().get(alias);
    if (folder == null){ return new IContributionItem[0]; }
    var state= link.get().state(alias, folder);
    var inFile= resource instanceof IFile f ? state.mains().entrySet().stream().filter(e->e.getValue().equals(fileOf(f))).map(Map.Entry::getKey).toList() : List.<String>of();
    var offered= inFile.isEmpty() ? List.copyOf(state.mains().keySet()) : inFile;
    var res= new ArrayList<IContributionItem>();
    if (state.needsCompiling()){ res.add(action("Compile Fearless project "+alias, ()->link.get().send("run", folder))); }
    if (offered.size() == 1){ res.add(action("Run Fearless "+offered.getFirst(), ()->link.get().send("run", folder, offered.getFirst()))); }
    if (offered.size() > 1){
      var menu= new MenuManager("Run Fearless");
      offered.forEach(m->menu.add(action(m, ()->link.get().send("run", folder, m))));
      res.add(menu);
    }
    if (!state.running().isEmpty()){ res.add(action("Terminate Fearless "+state.running(), ()->link.get().send("terminate", folder))); }
    return res.toArray(IContributionItem[]::new);
  }
  private static String fileOf(IFile f){ return f.getProjectRelativePath().removeFirstSegments(1).toString(); }
  private static IContributionItem action(String text, Runnable run){
    return new ActionContributionItem(new Action(text){ @Override public void run(){ run.run(); } });
  }
  private static IResource selectedResource(){
    var window= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (window == null || window.getActivePage() == null){ return null; }
    var selection= window.getSelectionService().getSelection();
    var res= selection instanceof IStructuredSelection s ? Adapters.adapt(s.getFirstElement(), IResource.class) : null;
    if (res != null){ return res; }
    var editor= window.getActivePage().getActiveEditor();
    return editor == null ? null : Adapters.adapt(editor.getEditorInput(), IResource.class);
  }
}
