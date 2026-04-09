package fearlessPluginProject.icons;

import java.util.List;
import org.eclipse.swt.graphics.Image;

public class IconSelector {
  public static Image iconForFile(List<String> chain){
    var fear= chain.getLast().endsWith(".fear");
    if (fear){ return Icons.imgSrc; }
    //if compileError starts with chain, errorFile
    return null;
  }
  public static Image iconForFolder(List<String> chain){
    //if compileError starts with chain, errorFolder
    if (!chain.getLast().startsWith("_")){ return Icons.imgFolder; }
    for (int i= 1; i < chain.size() - 1; i++){
      if (chain.get(i).startsWith("_")){ return Icons.imgErr; }//this need to be a different image wrt to the error folder at the top.
    }
    return Icons.imgPkg;
  }
  public static List<String> compileError=List.of();//this will be filled by the last compilation round
}