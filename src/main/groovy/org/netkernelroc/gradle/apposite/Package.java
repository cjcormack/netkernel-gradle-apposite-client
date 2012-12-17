package org.netkernelroc.gradle.apposite;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Node;
import nu.xom.Nodes;
import org.apache.commons.io.FileUtils;
import org.netkernelroc.gradle.NetKernelConvention;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

public class Package {
  private SortedSet<PackageVersion> versions = new TreeSet<PackageVersion>();
  private final RepositorySet set;
  private final long id;
  private final String name;
  private final String description;
  private final String maintainer;
  private final int runLevel;
  private final String section;
  private final String www;
  private final String license;
  private PackageVersion latestInstalledVersion;

  public Package(RepositorySet set, long id, String name, String description, String maintainer, int runLevel,
                 String section, String www, String license) {
    this.set = set;
    this.id = id;
    this.name = name;
    this.description = description;
    this.maintainer = maintainer;
    this.runLevel = runLevel;
    this.section = section;
    this.www = www;
    this.license = license;
  }

  public RepositorySet getSet() {
    return set;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getMaintainer() {
    return maintainer;
  }

  public int getRunLevel() {
    return runLevel;
  }

  public String getSection() {
    return section;
  }

  public String getWww() {
    return www;
  }

  public String getLicense() {
    return license;
  }

  public SortedSet<PackageVersion> getVersions() {
    return Collections.unmodifiableSortedSet(versions);
  }

  public boolean addVersion(PackageVersion version) {
    return versions.add(version);
  }

  public PackageVersion getLatestInstalledVersion() {
    return latestInstalledVersion;
  }

  public void setLatestInstalledVersion(PackageVersion latestInstalledVersion) {
    this.latestInstalledVersion = latestInstalledVersion;
  }

  public boolean isInstalled() {
    return latestInstalledVersion != null;
  }

  public boolean updateAvailable() {
    return isInstalled() && versions.last().compareTo(latestInstalledVersion) > 0;
  }

  public void update(Connection connection, File nkInstance) throws Exception {
    if (updateAvailable()) {
      uninstall(connection, nkInstance);
      install(connection, nkInstance);
    } else {
      System.out.println("No update available to install");
    }
  }

  public void install(Connection connection, File nkInstance) throws Exception {
    PackageVersion toInstall = versions.last();
    File packageFile = toInstall.download(nkInstance, connection);

    File expandedPackage = NetKernelConvention.createTempDir();
    NetKernelConvention.expandZip(packageFile, expandedPackage);

    Document manifestDocument = new Builder().build(new File(expandedPackage, "manifest.xml"));
    Nodes modulesNodes = manifestDocument.query("/manifest/module");

    final String setInstalledSql = "UPDATE PACKAGE_VERSIONS SET INSTALLED=TRUE WHERE ID=?;";
    final PreparedStatement setInstalledPS = connection.prepareStatement(setInstalledSql);

    final String addTransactionEventSql = "INSERT INTO PACKAGE_TRANSACTION_EVENTS VALUES (\n" +
                                          "    NULL,\n" +
                                          "    @TRANSACTIONID,\n" +
                                          "    1,\n" +
                                          "    ?\n" +
                                          ");";
    final PreparedStatement addTransactionEventPS = connection.prepareStatement(addTransactionEventSql);

    final String addModuleSql = "MERGE INTO MODULES (\n" +
                                "    PACKAGEVID,\n" +
                                "    IDENTITY,\n" +
                                "    VERSION,\n" +
                                "    LOCALSRC,\n" +
                                "    RUNLEVEL,\n" +
                                "    EXPANDED\n" +
                                ")\n" +
                                "KEY (IDENTITY, VERSION)\n" +
                                "VALUES (\n" +
                                "    ?,\n" +
                                "    ?,\n" +
                                "    ?,\n" +
                                "    ?,\n" +
                                "    ?,\n" +
                                "    ?\n" +
                                ");";
    final PreparedStatement addModulePS = connection.prepareStatement(addModuleSql);

    setInstalledPS.clearParameters();
    setInstalledPS.setLong(1, toInstall.getId());
    setInstalledPS.executeUpdate();

    addTransactionEventPS.clearParameters();
    addTransactionEventPS.setLong(1, id);
    addTransactionEventPS.executeUpdate();

    for (int moduleI = 0; moduleI < modulesNodes.size(); moduleI++) {
      Node moduleNode = modulesNodes.get(moduleI);

      String uri = moduleNode.query("uri").get(0).getValue();
      String version = moduleNode.query("version").get(0).getValue();
      int runLevel = Integer.parseInt(moduleNode.query("runlevel").get(0).getValue());
      String source = moduleNode.query("source").get(0).getValue();
      boolean expand = Boolean.parseBoolean(moduleNode.query("expand").get(0).getValue());

      Integer[] versionArray = RepositorySet.stringArrayToIntArray(version.split("\\."));

      File targetFile;
      if (uri.startsWith("urn:com:ten60:core:")) {
        expand = false;
        targetFile = new File(nkInstance, "lib");
      } else {
        targetFile = new File(nkInstance, "modules");
      }

      File moduleJar = new File(expandedPackage, source);

      String baseName = uri.replaceAll(":", ".") + "-" + version;

      File target;
      File jarTarget = new File(targetFile, baseName + ".jar");
      File expandedTarget = new File(targetFile, baseName);

      if (expand) {
        target = expandedTarget;
      } else {
        target = jarTarget;
      }

      if (target.exists()) {
        System.out.println("Not moving module into place as it already exists");
      } else {
        if (expand) {
          System.out.println("Expanding module " + uri + " to " + expandedTarget.getAbsolutePath());
          NetKernelConvention.expandZip(moduleJar, expandedTarget);
        } else {
          System.out.println("Moving module " + uri + " to " + jarTarget.getAbsolutePath());
          FileUtils.moveFile(moduleJar, jarTarget);
        }
      }

      addModulePS.clearParameters();
      addModulePS.setLong(1, toInstall.getId());
      addModulePS.setString(2, uri);
      addModulePS.setObject(3, versionArray);
      addModulePS.setString(4, nkInstance.toURI().relativize(target.toURI()).getPath());
      addModulePS.setInt(5, runLevel);
      addModulePS.setBoolean(6, expand);
      addModulePS.executeUpdate();
    }
    FileUtils.deleteDirectory(expandedPackage);

    latestInstalledVersion = toInstall;
  }

  public void uninstall(Connection connection, File nkInstance) throws Exception {
    final String setNotInstalledSql = "UPDATE PACKAGE_VERSIONS SET INSTALLED=FALSE WHERE ID=?";
    final PreparedStatement setNoInstalledPS = connection.prepareStatement(setNotInstalledSql);

    final String addTransactionEventSql = "INSERT INTO PACKAGE_TRANSACTION_EVENTS VALUES (\n" +
                                          "    NULL,\n" +
                                          "    @TRANSACTIONID,\n" +
                                          "    2,\n" +
                                          "    ?\n" +
                                          ");";
    final PreparedStatement addTransactionEventPS = connection.prepareStatement(addTransactionEventSql);

    final String deleteModulesSql = "DELETE FROM MODULES WHERE PACKAGEVID=?;";
    final PreparedStatement deleteModulesPS = connection.prepareStatement(deleteModulesSql);

    // I believe this only happens if the package was uploaded
    final String deletePackageSql = "DELETE\n" +
                                    "FROM   PACKAGES\n" +
                                    "WHERE  ID=( SELECT PACKAGES.ID\n" +
                                    "            FROM    PACKAGES,\n" +
                                    "                    PACKAGE_VERSIONS\n" +
                                    "            WHERE   PACKAGES.ID=PACKAGE_VERSIONS.PACKAGEID\n" +
                                    "            AND     PACKAGES.REPOCOLLECTIONSETID=1\n" +
                                    "            AND     PACKAGE_VERSIONS.ID=?\n" +
                                    "          );";
    final PreparedStatement deletePackagePS = connection.prepareStatement(deletePackageSql);

    setNoInstalledPS.clearParameters();
    setNoInstalledPS.setLong(1, latestInstalledVersion.getId());
    setNoInstalledPS.executeUpdate();

    addTransactionEventPS.clearParameters();
    addTransactionEventPS.setLong(1, id);
    addTransactionEventPS.executeUpdate();

    deletePackagePS.clearParameters();
    deletePackagePS.setLong(1, latestInstalledVersion.getId());
    deletePackagePS.executeUpdate();

    latestInstalledVersion = null;
  }

  @Override
  public String toString() {
    return name + " (" + versions.last().getVersionString() + ")";
  }
}
