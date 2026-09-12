package controller;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import tools.Fs;
import userMessages.Violation;

/// The two conventions of the host operating system that the manager needs:
/// - where this system keeps installed programs;
/// - where this system keeps what one program writes for one user.
/// A program the user unpacked somewhere carries its own data next to itself, so the
/// whole of it travels together and can be deleted by deleting one folder; a program a
/// system installed sits where the system decided, typically read only, and its data
/// goes to the per-user place that system keeps for exactly this.
final class Install{
  private Install(){}
  private static final Path home= Path.of(System.getProperty("user.home"));
  static boolean isInstalled(Path binDir){
    var here= binDir.toAbsolutePath().normalize();
    return roots().anyMatch(root->here.startsWith(root) && !here.equals(root));
  }
  static Path userDataHome(){
    if (Fs.isWindows()){ return env("LOCALAPPDATA").orElse(home.resolve("AppData","Local")); }
    if (Fs.isMac()){ return home.resolve("Library","Application Support"); }
    if (Fs.isLinux()){ return env("XDG_DATA_HOME").orElse(home.resolve(".local","share")); }
    throw Violation.unsupportedOperatingSystem();
  }
  private static Stream<Path> roots(){
    if (Fs.isWindows()){
      return Stream.concat(
        Stream.of("ProgramFiles","ProgramFiles(x86)","ProgramW6432").flatMap(n->env(n).stream()),
        Stream.of(env("LOCALAPPDATA").orElse(home.resolve("AppData","Local")).resolve("Programs")));
    }
    if (Fs.isMac()){ return Stream.of(Path.of("/Applications"),home.resolve("Applications")); }
    if (Fs.isLinux()){ return Stream.of(Path.of("/opt"),Path.of("/usr"),home.resolve(".local","opt"),home.resolve(".local","lib")); }
    throw Violation.unsupportedOperatingSystem();
  }
  //A relative or unusable value is treated as absent: these variables name a location on
  //this machine, and a relative one would move the folder with the working directory.
  private static Optional<Path> env(String name){
    var value= System.getenv(name);
    if (value == null || value.isBlank()){ return Optional.empty(); }
    try{ return Optional.of(Path.of(value)).filter(Path::isAbsolute).map(Path::normalize); }
    catch(InvalidPathException e){ return Optional.empty(); }
  }
}