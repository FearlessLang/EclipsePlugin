package fearlessPluginProject.launch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
//TODO: Unreviewd AI
final class FearlessProcess extends PlatformObject implements IProcess{
  private static final String pluginId= "fearlessPluginProject";

  private final ILaunch launch;
  private final String label;
  private final MessageConsole con;
  private final Path projectRoot;
  private final Path fearlessExe;
  private final Map<String,String> attrs= new HashMap<>();
  private final AtomicBoolean done= new AtomicBoolean();
  private final AtomicBoolean cancelRequested= new AtomicBoolean();

  private volatile Process process;
  private volatile Thread waitThread;
  private volatile int exitValue;
  private volatile JUnitPoller junitPoller;

  FearlessProcess(ILaunch launch,String label,MessageConsole con,Path projectRoot,Path fearlessExe){
    this.launch= launch;
    this.label= label;
    this.con= con;
    this.projectRoot= projectRoot;
    this.fearlessExe= fearlessExe;
  }

  void start() throws CoreException{
    if (process != null){ throw new IllegalStateException("start twice"); }

    Display.getDefault().syncExec(() -> Links.install(con,projectRoot));
    junitPoller= new JUnitPoller(projectRoot);
    junitPoller.start();

    try{
      var p= DebugPlugin.exec(cmd(),projectRoot.toFile());
      process= p;
      if (cancelRequested.get()){ p.destroy(); }
      startPump("FearlessOut",p.getInputStream());
      startPump("FearlessErr",p.getErrorStream());
      waitThread= new Thread(() -> waitFor(p),"FearlessWait");
      waitThread.setDaemon(true);
      waitThread.start();
    }
    catch(CoreException e){
      stopPoller();
      throw e;
    }
  }

  void startFailed(){
    exitValue= 1;
    fireTerminateOnce();
  }

  private String[] cmd(){
    return new String[]{ fearlessExe.toString(), projectRoot.toString() };
  }

  private void startPump(String name,InputStream in){
    var t= new Thread(() -> pump(in),name);
    t.setDaemon(true);
    t.start();
  }

  private void pump(InputStream in){
    try(in; var ms= stream()){
      in.transferTo(ms);
    }
    catch(IOException e){
      if (!done.get() && !cancelRequested.get()){ throw new UncheckedIOException(e); }
    }
  }

  private MessageConsoleStream stream(){
    var ms= con.newMessageStream();
    ms.setActivateOnWrite(true);
    ms.setEncoding("UTF-8");
    return ms;
  }

  private void waitFor(Process p){
    try{
      exitValue= p.waitFor();
    }
    catch(InterruptedException e){
      exitValue= terminateAndWait(p);
    }
    finally{
      try{ stopPoller(); }
      finally{ fireTerminateOnce(); }
    }
  }

  private static int terminateAndWait(Process p){
    p.destroy();
    try{
      if (!p.waitFor(500,TimeUnit.MILLISECONDS)){ p.destroyForcibly(); }
      return p.waitFor();
    }
    catch(InterruptedException e){
      p.destroyForcibly();
      try{ return p.waitFor(); }
      catch(InterruptedException ignored){
        Thread.currentThread().interrupt();
        return 143;
      }
    }
  }

  private void stopPoller(){
    var p= junitPoller;
    if (p != null){ p.stopAndFinalImport(); }
  }

  private void fireTerminateOnce(){
    if (!done.compareAndSet(false,true)){ return; }
    DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[]{ new DebugEvent(this,DebugEvent.TERMINATE) });
  }

  @Override public String getLabel(){ return label; }
  @Override public ILaunch getLaunch(){ return launch; }
  @Override public boolean isTerminated(){ return done.get(); }
  @Override public boolean canTerminate(){ return !done.get() && !cancelRequested.get(); }

  @Override public void terminate() throws DebugException{
    if (!cancelRequested.compareAndSet(false,true)){ return; }
    var p= process;
    if (p != null){ p.destroy(); }
    var t= waitThread;
    if (t != null){ t.interrupt(); }
  }

  @Override public IStreamsProxy getStreamsProxy(){ return null; }

  @Override public int getExitValue() throws DebugException{
    if (!done.get()){
      throw new DebugException(new Status(IStatus.ERROR,pluginId,"Not terminated"));
    }
    return exitValue;
  }

  @Override public void setAttribute(String key,String value){ attrs.put(key,value); }
  @Override public String getAttribute(String key){ return attrs.get(key); }

  private static final class Links{
    private static final Pattern stackCapture= Pattern.compile(
      "(?:error\\s+)?line:\\s*(\\d+)\\s*in file\\s*([a-z0-9_./]+\\.fear)\\b"
    );
    private static final Pattern stackDetect= Pattern.compile(
      "(?:error\\s+)?line:\\s*\\d+\\s*in file\\s*[a-z0-9_./]+\\.fear\\b"
    );
    private static final Pattern inFileCapture= Pattern.compile(
      "In file:\\s*(?:fear:/)?([a-z0-9_./]+\\.fear)\\b"
    );
    private static final Pattern numberedLine= Pattern.compile("^(\\d{3,})\\|");
    private static final Pattern caretLine= Pattern.compile("^\\s*\\|\\s*\\^+");

    static void install(MessageConsole con,Path projectRoot){
      con.addPatternMatchListener(new StackFrames(con,projectRoot));
      con.addPatternMatchListener(new InFileHeaders(con,projectRoot));
    }

    private static final class StackFrames implements IPatternMatchListener{
      private final MessageConsole con;
      private final Path projectRoot;
      private volatile TextConsole console;

      StackFrames(MessageConsole con,Path projectRoot){
        this.con= con;
        this.projectRoot= projectRoot;
      }

      @Override public void connect(TextConsole console){ this.console= console; }
      @Override public void disconnect(){ this.console= null; }
      @Override public String getPattern(){ return stackDetect.pattern(); }
      @Override public int getCompilerFlags(){ return 0; }
      @Override public String getLineQualifier(){ return null; }

      @Override public void matchFound(PatternMatchEvent event){
        var c= console;
        if (c == null){ return; }
        try{
          var doc= c.getDocument();
          var off= event.getOffset();
          var len= event.getLength();
          var s= doc.get(off,len);

          var m= stackCapture.matcher(s);
          if (!m.find()){ return; }

          var line= Integer.parseInt(m.group(1));
          var rel= m.group(2);
          var relOff= off + m.start(2);
          con.addHyperlink(new FileLineLink(projectRoot,rel,line),relOff,rel.length());
        }
        catch(BadLocationException e){ throw new IllegalStateException(e); }
      }
    }

    private static final class InFileHeaders implements IPatternMatchListener{
      private final MessageConsole con;
      private final Path projectRoot;
      private volatile TextConsole console;

      InFileHeaders(MessageConsole con,Path projectRoot){
        this.con= con;
        this.projectRoot= projectRoot;
      }

      @Override public void connect(TextConsole console){ this.console= console; }
      @Override public void disconnect(){ this.console= null; }
      @Override public String getPattern(){ return inFileCapture.pattern(); }
      @Override public int getCompilerFlags(){ return 0; }
      @Override public String getLineQualifier(){ return null; }

      @Override public void matchFound(PatternMatchEvent event){
        var c= console;
        if (c == null){ return; }
        try{
          var doc= c.getDocument();
          var off= event.getOffset();
          var len= event.getLength();
          var s= doc.get(off,len);

          var m= inFileCapture.matcher(s);
          if (!m.find()){ return; }

          var rel= m.group(1);
          var relOff= off + m.start(1);
          con.addHyperlink(new InFileLink(c,projectRoot,off,rel),relOff,rel.length());
        }
        catch(BadLocationException e){ throw new IllegalStateException(e); }
      }
    }

    private record FileLineLink(Path projectRoot,String rel,int line1) implements IHyperlink{
      @Override public void linkActivated(){ openAt(projectRoot.resolve(rel),line1); }
      @Override public void linkEntered(){}
      @Override public void linkExited(){}
    }

    private record InFileLink(TextConsole console,Path projectRoot,int headerOffset,String rel) implements IHyperlink{
      @Override public void linkActivated(){
        var line= inferLineFromCaret(console.getDocument(),headerOffset);
        openAt(projectRoot.resolve(rel),line);
      }
      @Override public void linkEntered(){}
      @Override public void linkExited(){}
    }

    private static int inferLineFromCaret(IDocument doc,int headerOffset){
      try{
        var startLine= doc.getLineOfOffset(headerOffset);
        Integer lastNum= null;

        for (int i=startLine+1;i<doc.getNumberOfLines();++i){
          var li= doc.getLineInformation(i);
          var s= doc.get(li.getOffset(),li.getLength());

          var m1= numberedLine.matcher(s);
          if (m1.find()){
            lastNum= Integer.parseInt(m1.group(1));
            continue;
          }
          if (caretLine.matcher(s).find()){
            if (lastNum == null){ throw new IllegalStateException("Caret without numbered line"); }
            return lastNum;
          }
          if (lastNum != null && !s.isBlank()){ break; }
        }
        throw new IllegalStateException("No caret line after In file:");
      }
      catch(BadLocationException e){ throw new IllegalStateException(e); }
    }

    private static void openAt(Path abs,int line1){
      try{
        var files= ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(abs.toUri());
        if (files.length != 1){ throw new IllegalStateException("Expected 1 IFile for "+abs+" got "+files.length); }
        IFile file= files[0];

        var win= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (win == null){ throw new IllegalStateException("No active workbench window"); }
        var page= win.getActivePage();
        if (page == null){ throw new IllegalStateException("No active workbench page"); }

        var part= IDE.openEditor(page,file,true);
        if (part instanceof ITextEditor te){
          var doc= te.getDocumentProvider().getDocument(te.getEditorInput());
          revealLine(te,doc,line1);
        }
      }
      catch(PartInitException e){ throw new IllegalStateException(e); }
    }

    private static void revealLine(ITextEditor te,IDocument doc,int line1){
      try{
        var line0= line1-1;
        var info= doc.getLineInformation(line0);
        te.selectAndReveal(info.getOffset(),info.getLength());
      }
      catch(BadLocationException e){ throw new IllegalStateException("Bad line "+line1,e); }
    }
  }
}