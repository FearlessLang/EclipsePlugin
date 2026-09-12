module Controller {
  requires transitive Coordinator;
  requires java.desktop;
  exports controller;
  exports gui;
}