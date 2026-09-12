package mainController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.Fs;
import tools.JavacTool;
import tools.JavaTool;
import utils.OneOr;

public class ControllerBuild{
  static final Path commonsSrc= LocalResources.commons.resolve("src");
  static final Path frontendSrc= LocalResources.frontend.resolve("src");
  static final Path frontendSrcModule= LocalResources.frontend.resolve("srcModule");
  static final Path coordinatorSrc= LocalResources.coordinator.resolve("src");
  static final Path coordinatorSrcModule= LocalResources.coordinator.resolve("srcModule");
  static final Path controllerSrc= LocalResources.controller.resolve("src");
  static final Path controllerSrcModule= LocalResources.controller.resolve("srcModule");
  static final Path externalJars= LocalResources.coordinator.resolve("externalJars");
  static final Path testJars= LocalResources.coordinator.resolve("testJars");
  static final Path packaging= LocalResources.coordinator.resolve("_fearless_packaging");
  static final Path base= LocalResources.stLib.resolve("base");
  static final Path rt= LocalResources.stLib.resolve("rt");
  static final Path pluginJars= LocalResources.controller.resolve("Artefact").resolve("plugins");
  static final Path out= LocalResources.controller.getParent().resolve("out").resolve("controller");
  static final Path mods= out.resolve("mods");
  static final String versionId= "0_001";
  static final String appName= "fearlessManaged"+versionId;

  static void baseJars(){
    Fs.cleanDir(mods); Fs.ensureDir(mods);
    Fs.copyTreeFlat(externalJars, mods);
    Fs.copyTreeFlat(testJars, mods);
    buildJar("Commons", List.of(commonsSrc));
    buildJar("FearlessFrontend", List.of(frontendSrc, frontendSrcModule));
  }
  static void coordinatorJar(){
    buildJar("Coordinator", List.of(coordinatorSrc, coordinatorSrcModule));
  }
  static void coordinatorTest(){
    var co= LocalResources.coordinator;
    JavacTool.javac(List.of(coordinatorSrc, co.resolve("test"), co.resolve("testModule")), out.resolve("coordinator-test"), mods);
  }
  static void controllerTest(){
    var co= LocalResources.controller;
    JavacTool.javac(List.of(controllerSrc, co.resolve("test"), co.resolve("testModule")), out.resolve("controller-test"), mods);
  }
  static void buildJar(String name, List<Path> srcs){
    var classes= out.resolve(name);
    JavacTool.javac(srcs, classes, mods);
    JavacTool.jar(classes, mods.resolve(name+".jar"));
  }

  static void runJUnit(Path testClasses) throws InterruptedException{
    var args= new ArrayList<String>(List.of("execute",
      "--class-path", testClasses.toString(), "--scan-class-path="+testClasses,
      "--include-classname=.*", "--details=summary", "--disable-ansi-colors"));
    JavaTool.runMain(List.of("-ea"), testClasses, mods, "org.junit.platform.console.ConsoleLauncher", args.toArray(String[]::new));
  }

  static void deployBaseCache(Path appRoot) throws InterruptedException{
    JavaTool.runMain(List.of("-ea"), out.resolve("coordinator-test"), mods, "mainCoordinator.BaseCacheBuilder", appRoot.toString());
  }

  static void deployEclipsePlugin(Path appRoot){
    var found= Fs.walk(appRoot, s->s.filter(Files::isDirectory).filter(p->p.getFileName().toString().equals("mods")).toList());
    var appDir= OneOr.of("Expected exactly one 'mods' dir under "+appRoot, found.stream()).getParent();
    Fs.copyFresh(pluginJars, appDir.resolve("eclipsePlugin"));
  }
}