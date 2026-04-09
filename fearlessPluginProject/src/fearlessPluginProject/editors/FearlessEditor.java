package fearlessPluginProject.editors;

import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.ui.editors.text.TextEditor;

public final class FearlessEditor extends TextEditor{
  public FearlessEditor(){
    setDocumentProvider(new FileDocumentProvider());
  }
}
