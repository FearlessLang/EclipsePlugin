package fearlessPluginProject;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;

/// Turns the manager's problems.txt (see managerInfo.ProblemReport in Coordinator) into
/// exactly one Eclipse problem marker per project, replacing whatever marker was there before.
/// problems.txt format: relative file path, then line number, then the raw message - each on
/// its own line, message running to end of file. An empty/missing file means "no problem".
public final class ProblemMarkers{
  public static final String type= "fearlessPluginProject.problem";
  private ProblemMarkers(){}
  public static void apply(IProject project, String text){
    try{ project.deleteMarkers(type, true, IResource.DEPTH_INFINITE); }
    catch(CoreException e){ return; }
    if (text.isBlank()){ return; }
    var lines= text.split("\n", 3);
    if (lines.length < 3){ return; }
    var relPath= lines[0].strip();
    int line;
    try{ line= Integer.parseInt(lines[1].strip()); }
    catch(NumberFormatException e){ return; }
    var file= project.getFile(new Path(relPath));
    if (!file.exists()){ return; }
    createMarker(file, line, lines[2]);
  }
  private static void createMarker(IFile file, int line, String message){
    try{
      var marker= file.createMarker(type);
      marker.setAttribute(IMarker.LINE_NUMBER, line);
      marker.setAttribute(IMarker.MESSAGE, message);
      marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
    }
    catch(CoreException e){}
  }
}
