// java --module-path ../../Commons/Commons.jar --add-modules Commons mainController/DeployManagedFearless.java
package mainController;

import java.util.List;
import tools.PortableApp;

public class DeployManagedFearless{
  public static void main(String[] a) throws InterruptedException{
    var appRoot= LocalResources.managedFolderOut.resolve(ControllerBuild.appName);
    new PortableApp(
      ControllerBuild.packaging,
      LocalResources.managedFolderOut,//out
      List.of(
        List.of(ControllerBuild.commonsSrc),
        List.of(ControllerBuild.frontendSrc, ControllerBuild.frontendSrcModule),
        List.of(ControllerBuild.coordinatorSrc, ControllerBuild.coordinatorSrcModule),
        List.of(ControllerBuild.controllerSrc, ControllerBuild.controllerSrcModule)),
      ControllerBuild.base,
      ControllerBuild.rt,
      ControllerBuild.externalJars,
      ControllerBuild.appName,
      ControllerBuild.versionId,
      "Controller/manager.ManagerMain"
    ).build();
    ControllerBuild.baseJars();
    ControllerBuild.coordinatorTest();
    ControllerBuild.deployBaseCache(appRoot);
    ControllerBuild.deployEclipsePlugin(appRoot);
  }
}