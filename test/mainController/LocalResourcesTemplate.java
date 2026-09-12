package mainController;

import java.nio.file.Path;

public class LocalResourcesTemplate { //public class LocalResources {
  //example for windows
  private static Path prefix=Path.of("C:\\").resolve("Users","...","OneDrive","Documents","GitHub");
  //example for linux
  //private static Path prefix= Path.of("/").resolve("home","...","Desktop","Java25");
  //example for mac
  //private static Path prefix= ...

  static public final Path commons= prefix.resolve("Commons");
  static public final Path frontend= prefix.resolve("Frontend","FearlessFrontend");
  static public final Path coordinator= prefix.resolve("Coordinator");
  static public final Path controller= prefix.resolve("EclipsePlugin");
  static public final Path stLib= prefix.resolve("StandardLibrary");
  static public final Path managedFolderOut= prefix.resolve("StandardLibrary","fearlessManagedArtefact");
}