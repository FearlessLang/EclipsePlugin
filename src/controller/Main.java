package controller;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import controller.Registry.Entry;
import controller.Registry.Kind;
import fileSupport.NativeLocaleForcer;
import mainCoordinator.MakeDemo;
import realSourceOracle.AutoloadHandler;
import fileSupport.StringFiles;
import gui.Tray;
import gui.Window;
import tools.Fs;
import tools.JavacTool;
import userMessages.Report;
import userMessages.UserError;
import userMessages.Violation;
import utils.Bug;

/// The manager process. Every launch leaves one message file (the folder it was
/// started on) in the manager folder, then tries to take the instance lock: the one
/// process holding it owns the window and drains every message, its own included.
/// Resources that live for the whole process life (the lock, the watch service, the
/// workers, the window) are never closed by us: every path out of main leads to
/// System.exit, and the operating system reclaims them on process death.
public final class Main{
  private static FileLock lock;
  public final Path managerDir;
  public final Registry registry;
  public final Eclipse eclipse;
  public final ExecutorService worker= Executors.newVirtualThreadPerTaskExecutor();
  private final Stop stop= new Stop();
  private Window window;
  private Main(Path managerDir){
    this.managerDir= managerDir;
    registry= new Registry(managerDir);
    eclipse= new Eclipse(managerDir.resolve("eclipse"));
  }
  public Path msgDir(){ return managerDir.resolve("messages"); }
  public static void main(String[] args){
    NativeLocaleForcer.forceEnglish();
    var exitCode= 0;
    try{ run(args.length == 0 ? "" : args[0]); }
    catch(UserError e){ exitCode= 1; display(e); }
    catch(VirtualMachineError|LinkageError e){ exitCode= 2; display(Violation.vmOrLinkageFailure(e)); }
    catch(Throwable t){ exitCode= 3; display(UserError.crashed(t)); }
    System.exit(exitCode);
  }
  private static void display(UserError e){
    try{ e.display(); }
    catch(InterruptedException ie){ e.displayStderr(ie); }
  }
  private static void run(String message){
    var binDir= binDir();
    var host= Install.isInstalled(binDir) ? Install.userDataHome() : binDir.getParent();
    var main= new Main(host.resolve(JavacTool.dataDirNameFor(versionId())));
    try{ Files.createDirectories(main.msgDir()); }
    catch(IOException|UnsupportedOperationException|SecurityException e){ throw Violation.couldNotCreateManagerFolder(main.managerDir,e); }
    leave(main.msgDir(),message);
    var lockFile= main.managerDir.resolve("instance.lock");
    try{ lock= FileChannel.open(lockFile,CREATE,WRITE).tryLock(); }
    catch(OverlappingFileLockException e){ throw Bug.unreachable(); }
    catch(IOException e){ throw Violation.couldNotUseInstanceLock(lockFile,e); }
    if (lock == null){ return; }
    UserError.becameManagerOwner();
    main.own();
  }
  private static String versionId(){ return JavacTool.reqVersionId(Violation::mustUseLauncher); }
  private static Path binDir(){
    var expected= "fearlessManaged"+versionId()+(Fs.isMac() ? ".app" : "");
    var startedFrom= JavacTool.reqAppDir(Violation::mustUseLauncher).toAbsolutePath().normalize();
    for(var dir= startedFrom; dir != null && dir.getFileName() != null; dir= dir.getParent()){
      if (dir.getFileName().toString().equals(expected)){ return dir; }
    }
    throw Violation.programFolderNotFound(startedFrom,expected);
  }
  //Write to a .tmp name, then atomically rename it to .msg: the owner drains
  //only *.msg, so it can never observe a half written file.
  private static void leave(Path msgDir, String message){
    var name= "%020d-%s".formatted(System.currentTimeMillis(),UUID.randomUUID());
    var tmp= msgDir.resolve(name+".tmp");
    StringFiles.writeNew(tmp,message,UserError.onFileError());
    try{ Files.move(tmp,msgDir.resolve(name+".msg"),ATOMIC_MOVE); }
    catch(IOException e){ throw Violation.couldNotLeaveStartMessage(msgDir,e); }
  }
  private void own(){
    WatchService watcher;
    try{ watcher= FileSystems.getDefault().newWatchService(); msgDir().register(watcher,ENTRY_CREATE); }
    catch(IOException|UnsupportedOperationException|SecurityException e){ throw Violation.couldNotWatchMessageFolder(msgDir(),e); }
    window= Window.create(this);
    UserError.owner(window.frame);
    Violation.running(window::runningPrograms);
    Tray.install(window,this::quit);
    window.show();
    drain();
    worker.submit(stop.worker(()->watch(watcher)));
    if (!Fs.isMac()){ Association.launcher().ifPresent(l->Association.reconcile(l,Association.extensions(l))); }
    try{ stop.await(); }
    finally{ worker.shutdownNow(); }
  }
  public void quit(){ stop.quit(); }
  public void forgetAssociation(){
    if (Fs.isMac() || !window.askForget()){ return; }
    try{ Association.launcher().ifPresent(l->Association.reconcile(l,List.of())); }
    catch(UserError e){ display(e); System.exit(1); }
    System.exit(0);
  }
  private void watch(WatchService watcher){
    while(true){
      WatchKey key;
      try{ key= watcher.take(); }
      catch(InterruptedException e){ return; }
      key.pollEvents();
      drain();
      if (!key.reset()){ throw Violation.messageFolderNotWatchable(msgDir()); }
    }
  }
  //Runs once before the watcher worker starts, then only from that worker: never concurrently.
  private void drain(){
    List<String> messages;
    try{ messages= take(); }
    catch(IOException e){ throw Violation.couldNotDrainMessageFolder(msgDir(),e); }
    if (messages.isEmpty()){ return; }
    window.show();
    messages.forEach(m->register(m,e->eclipse.note(e.getMessage()+"\n")));
    window.foldersChanged();
  }
  private List<String> take() throws IOException{
    var files= list("*.msg");
    files.sort(Comparator.comparing(f->f.getFileName().toString()));
    var messages= new ArrayList<String>();
    for(var file: files){
      messages.add(StringFiles.read(file,UserError.onFileError()));
      Files.deleteIfExists(file);
    }
    var old= Instant.now().minusSeconds(60);
    for(var file: list("*.tmp")){
      try{ if (Files.getLastModifiedTime(file).toInstant().isBefore(old)){ Files.deleteIfExists(file); } }
      catch(NoSuchFileException e){}
    }
    return messages;
  }
  private List<Path> list(String glob) throws IOException{
    var files= new ArrayList<Path>();
    try(var stream= Files.newDirectoryStream(msgDir(),glob)){ stream.forEach(files::add); }
    return files;
  }
  //A message is the folder to select, or a verb, a newline, then the folder, then
  //for run the optional main to run, and for state the file the answer goes to.
  public void register(String message, Consumer<UserError> report){
    var lines= message.lines().toList();
    if (lines.isEmpty()){ return; }
    var verb= lines.size() == 1 ? "select" : lines.getFirst();
    var folder= projectFolder(lines.get(lines.size() == 1 ? 0 : 1),managerDir);
    if (folder.isEmpty()){ return; }
    if (!registry.has(folder.get())){
      var nested= registry.overlapping(folder.get());
      if (nested.isPresent()){ report.accept(Report.folderNestedWithRegistered(folder.get(),nested.get())); return; }
      var wanted= Names.compactName(folder.get());
      var fresh= Fs.of(()->{ try(var s= Files.list(folder.get())){ return s.findAny().isEmpty(); } });
      var taken= registry.all().stream().map(Entry::alias).collect(Collectors.toSet());
      var alias= Names.makeUnique(folder.get(),taken);
      if (!alias.equals(wanted)){ report.accept(Report.projectNamed(folder.get(),wanted,alias)); }
      Fs.rmTree(folder.get().resolve(Facts.outDir));
      Fs.rmTree(eclipse.reports(alias));
      registry.add(alias,folder.get());
      if (fresh){
        registry.update(folder.get(),e->e.withKind(Kind.code));
        MakeDemo.hello(folder.get(),Names.pkgName(alias),AutoloadHandler.capFirst(alias));
      }
      window.foldersChanged();
    }
    window.select(folder.get());
    switch(verb){
      case "select" -> {}
      case "run" -> window.run(folder.get(),lines.size() > 2 ? Optional.of(lines.get(2)) : Optional.empty());
      case "terminate" -> window.terminate(folder.get());
      case "state" -> window.state(folder.get(),Path.of(lines.get(2)));
      default -> throw Bug.unreachable();
    }
  }
  //The manager folder is not a project: a Fearless started on it registers nothing.
  static Optional<Path> projectFolder(String message, Path managerDir){
    if (message.isBlank()){ return Optional.empty(); }
    Path path;
    try{ path= Path.of(message).toAbsolutePath().normalize(); }
    catch(InvalidPathException e){ return Optional.empty(); }
    var folder= Files.isDirectory(path) ? Optional.of(path) : Files.isRegularFile(path) ? Optional.ofNullable(path.getParent()) : Optional.<Path>empty();
    return folder.filter(f->!f.equals(managerDir.toAbsolutePath().normalize()));
  }
}
//Note: A worker stopped by shutdownNow records a problem that nobody ever reads; this is harmless.
class Stop{
  private final CountDownLatch latch= new CountDownLatch(1);
  private final AtomicReference<RuntimeException> failure= new AtomicReference<>();
  void quit(){ latch.countDown(); }
  Runnable worker(Runnable task){ return ()->runWorker(task); }
  private void runWorker(Runnable task){
    try{ task.run(); fail(Bug.of("A manager worker stopped, but it must run for the whole life of the manager process")); }
    catch(UserError problem){ fail(problem); }
    catch(Throwable t){ fail(Bug.of(t)); }
  }
  private void fail(RuntimeException problem){ failure.compareAndSet(null,problem); latch.countDown(); }
  void await(){
    try{ latch.await(); }
    catch(InterruptedException e){ throw Bug.of(e); }
    var problem= failure.get();
    if (problem != null){ throw problem; }
  }
}