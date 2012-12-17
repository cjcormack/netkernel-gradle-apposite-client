package org.netkernelroc.gradle.apposite;

import nu.xom.Attribute;
import nu.xom.Element;

public class ModuleEntry {
  private final String path;
  private final String runLevel;
  private final String identity;

  public ModuleEntry(String path, String runLevel, String identity) {
    this.path = path;
    this.runLevel = runLevel;
    this.identity = identity;
  }

  public String getPath() {
    return path;
  }

  public String getRunLevel() {
    return runLevel;
  }

  public Element toElement() {
    Element elem = new Element("module");
    elem.addAttribute(new Attribute("runlevel", runLevel));
    elem.appendChild(path);
    return elem;
  }

  public String getIdentity() {
    return identity;
  }
}
