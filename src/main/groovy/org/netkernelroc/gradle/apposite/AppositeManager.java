package org.netkernelroc.gradle.apposite;

import nu.xom.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class AppositeManager {
  private File installDirectory;

  private Connection noAutoCommitConnection;

  private List<Repository> repositories;
  private List<Package> packages;

  public AppositeManager(File installDirectory) throws SQLException {
    this.installDirectory = installDirectory;

    noAutoCommitConnection = DriverManager.getConnection("jdbc:h2:" + new File(installDirectory, "etc/apposite/packagesDB") + ";WRITE_DELAY=0");
    noAutoCommitConnection.setAutoCommit(false);

    repositories = loadRepositories();
    packages = loadPackages();
  }

  private List<Repository> loadRepositories() throws SQLException {
    final String repoListSql = "SELECT REPOS.ID,\n" +
                               "       REPOS.NAME,\n" +
                               "       REPOS.URLBASE,\n" +
                               "       CASEWHEN(REPOS.PUBLICKEY IS NULL, 0, 1) AS HASPUBLICKEY, \n" +
                               "       REPOS.TRUSTED\n" +
                               "FROM   REPOS\n" +
                               "WHERE  REPOS.ID>1;  --Ignore the dev repo";

    final PreparedStatement repoListStmt = noAutoCommitConnection.prepareStatement(repoListSql);

    final String collectionListSql = "SELECT REPO_COLLECTIONS.ID,\n" +
                                     "       REPO_COLLECTIONS.DISTPATH\n" +
                                     "FROM   REPO_COLLECTIONS\n" +
                                     "WHERE  REPO_COLLECTIONS.REPOID=?";
    final PreparedStatement collectionsListStmt = noAutoCommitConnection.prepareStatement(collectionListSql);

    final String setsListSql = "SELECT REPO_COLLECTION_SETS.ID,\n" +
                               "       REPO_COLLECTION_SETS.NAME\n" +
                               "FROM   REPO_COLLECTION_SETS\n" +
                               "WHERE  REPO_COLLECTION_SETS.REPOCOLLECTIONID=?";
    final PreparedStatement setsListStmt = noAutoCommitConnection.prepareStatement(setsListSql);

    ResultSet repoRS = repoListStmt.executeQuery();

    List<Repository> repositories = new ArrayList<Repository>();

    while (repoRS.next()) {
      Repository repository = new Repository(repoRS.getLong("id"),
                                             repoRS.getString("name"),
                                             repoRS.getString("URLBASE"),
                                             repoRS.getBoolean("HASPUBLICKEY"),
                                             repoRS.getBoolean("trusted"));

      collectionsListStmt.clearParameters();
      collectionsListStmt.setLong(1, repository.getId());
      ResultSet collectionsRS = collectionsListStmt.executeQuery();

      while (collectionsRS.next()) {
        RepositoryCollection collection = new RepositoryCollection(repository, collectionsRS.getLong("id"), collectionsRS.getString("distpath"));

        setsListStmt.clearParameters();
        setsListStmt.setLong(1, collection.getId());
        ResultSet setRS = setsListStmt.executeQuery();

        while (setRS.next()) {
          collection.addSet(new RepositorySet(collection, setRS.getLong("id"), setRS.getString("name")));
        }
        repository.addCollection(collection);
      }

      repositories.add(repository);
    }
    return repositories;
  }

  private List<Package> loadPackages() throws SQLException {
    List<Package> packages = new ArrayList<Package>();

    for (Repository repository : repositories) {
      for (RepositoryCollection collection : repository.getCollections()) {
        for (RepositorySet set : collection.getSets()) {
          packages.addAll(set.loadPackages(noAutoCommitConnection));
        }
      }
    }
    return packages;
  }

  public void synchronize() throws Exception {
    for (Repository repository : repositories) {
      for (RepositoryCollection collection : repository.getCollections()) {
        for (RepositorySet set : collection.getSets()) {
          set.synchronize(noAutoCommitConnection);
        }
      }
    }

    noAutoCommitConnection.createStatement().executeUpdate("UPDATE PACKAGE_VERSIONS SET INSTALLED=FALSE WHERE INSTALLED IS NULL;");
    noAutoCommitConnection.createStatement().executeUpdate("UPDATE PACKAGE_VERSIONS\n" +
                                                           "SET    HASUPDATE=TRUE\n" +
                                                           "WHERE  HASUPDATE IS NULL\n" +
                                                           "AND    PACKAGE_VERSIONS.INSTALLED=TRUE\n" +
                                                           "AND    PACKAGE_VERSIONS.PACKAGEID\n" +
                                                           "IN     ( SELECT PACKAGEID\n" +
                                                           "         FROM   PACKAGE_VERSIONS\n" +
                                                           "         WHERE  ID\n" +
                                                           "         IN     ( SELECT ID\n" +
                                                           "                  FROM ( SELECT A.*\n" +
                                                           "                         FROM   PACKAGE_VERSIONS AS A\n" +
                                                           "                         JOIN   ( SELECT   PACKAGEID,\n" +
                                                           "                                           MAX(PACKAGE_VERSIONS.VERSION) as VERSION\n" +
                                                           "                                  FROM     PACKAGE_VERSIONS\n" +
                                                           "                                  GROUP BY PACKAGEID\n" +
                                                           "                                ) AS B\n" +
                                                           "                         ON     A.PACKAGEID=B.PACKAGEID" +
                                                           "                         AND    A.VERSION=B.VERSION\n" +
                                                           "                       )\n" +
                                                           "                )\n" +
                                                           "         AND    INSTALLED=FALSE\n" +
                                                           "         AND    TYPE=2\n" +
                                                           "       );");
    noAutoCommitConnection.createStatement().executeUpdate("UPDATE PACKAGE_VERSIONS\n" +
                                                                   "SET    HASSECURITY=TRUE\n" +
                                                                   "WHERE  HASSECURITY IS NULL\n" +
                                                                   "AND    PACKAGE_VERSIONS.INSTALLED=TRUE\n" +
                                                                   "AND    PACKAGE_VERSIONS.PACKAGEID\n" +
                                                                   "IN     ( SELECT PACKAGEID\n" +
                                                                   "         FROM   PACKAGE_VERSIONS\n" +
                                                                   "         WHERE  ID\n" +
                                                                   "         IN     ( SELECT ID\n" +
                                                                   "                  FROM   ( SELECT A.*\n" +
                                                                   "                           FROM   PACKAGE_VERSIONS AS A\n" +
                                                                   "                           JOIN   ( SELECT   PACKAGEID,\n" +
                                                                   "                                             MAX(PACKAGE_VERSIONS.VERSION) as VERSION\n" +
                                                                   "                                    FROM     PACKAGE_VERSIONS\n" +
                                                                   "                                    GROUP BY PACKAGEID\n" +
                                                                   "                                  ) AS B\n" +
                                                                   "                           ON     A.PACKAGEID=B.PACKAGEID AND A.VERSION=B.VERSION\n" +
                                                                   "                         )\n" +
                                                                   "                )\n" +
                                                                   "         AND    INSTALLED=FALSE\n" +
                                                                   "         AND    TYPE=3\n" +
                                                                   "       );");
    noAutoCommitConnection.commit();
    packages = loadPackages();
  }

  public void startAppositeTransaction(TransactionContext tc) throws SQLException, IOException, ParsingException {
    tc.existingModules = getAppositeModules();
    tc.developerModules = getNonAppositeModules(tc.existingModules);

    noAutoCommitConnection.createStatement().executeUpdate("BEGIN;");
    noAutoCommitConnection.createStatement().executeUpdate("INSERT INTO PACKAGE_TRANSACTIONS VALUES (NULL, NOW());");
    noAutoCommitConnection.createStatement().executeUpdate("SET @TRANSACTIONID=IDENTITY();");
  }

  private Map<String, ModuleEntry> getNonAppositeModules(Map<String, ModuleEntry> existingModules) throws IOException, ParsingException {
    Document moduleXmlDocument = new Builder().build(new File(installDirectory, "etc/modules.xml"));

    Map<String, ModuleEntry> developerModules = new LinkedHashMap<String, ModuleEntry>();

    Nodes moduleNodes = moduleXmlDocument.query("/modules/module");

    for (int moduleI = 0; moduleI < moduleNodes.size(); moduleI++) {
      Node moduleNode = moduleNodes.get(moduleI);
      String path = moduleNode.getValue();
      String runLevel = moduleNode.query("@runlevel").get(0).getValue();
      if (!existingModules.containsKey(path)) {
        developerModules.put(path, new ModuleEntry(path, runLevel, null));
      }
    }

    return developerModules;
  }

  private Map<String, ModuleEntry> getAppositeModules() throws SQLException {
    final String modulesSql = "SELECT   MODULES.LOCALSRC,\n" +
                              "         MODULES.RUNLEVEL,\n" +
                              "         MODULES.IDENTITY\n" +
                              "FROM     MODULES,\n" +
                              "         PACKAGE_VERSIONS\n" +
                              "WHERE    MODULES.PACKAGEVID=PACKAGE_VERSIONS.ID\n" +
                              "AND      PACKAGE_VERSIONS.INSTALLED=TRUE\n" +
                              "AND      RUNLEVEL > 0\n" +
                              "ORDER BY RUNLEVEL,\n" +
                              "         IDENTITY ASC;";
    ResultSet modulesRS = noAutoCommitConnection.createStatement().executeQuery(modulesSql);

    Map<String, ModuleEntry> existingModules = new LinkedHashMap<String, ModuleEntry>();
    while (modulesRS.next()) {
      String path = modulesRS.getString("localsrc");
      String runLevel = modulesRS.getString("runlevel");
      String identity = modulesRS.getString("runlevel");
      existingModules.put(path, new ModuleEntry(path, runLevel, identity));
    }
    return existingModules;
  }

  private List<ModuleEntry> getAppositeModulesForRunLevel(int runLevel) throws SQLException {
    final String modulesSql = "SELECT   MODULES.LOCALSRC,\n" +
                              "         MODULES.RUNLEVEL,\n" +
                              "         MODULES.IDENTITY\n" +
                              "FROM     MODULES,\n" +
                              "         PACKAGE_VERSIONS\n" +
                              "WHERE    MODULES.PACKAGEVID=PACKAGE_VERSIONS.ID\n" +
                              "AND      PACKAGE_VERSIONS.INSTALLED=TRUE\n" +
                              "AND      RUNLEVEL = ?\n" +
                              "ORDER BY RUNLEVEL,\n" +
                              "         IDENTITY ASC;";
    final PreparedStatement modulesPS = noAutoCommitConnection.prepareStatement(modulesSql);
    modulesPS.clearParameters();
    modulesPS.setInt(1, runLevel);

    ResultSet modulesRS = modulesPS.executeQuery();

    List<ModuleEntry> modulesForRunLevel = new ArrayList<ModuleEntry>();
    while (modulesRS.next()) {
      String path = modulesRS.getString("localsrc");
      String identity = modulesRS.getString("identity");
      modulesForRunLevel.add(new ModuleEntry(path, runLevel + "", identity));
    }
    return modulesForRunLevel;
  }

  public void endAppositeTransaction(TransactionContext tc) throws SQLException, IOException {
    noAutoCommitConnection.createStatement().executeUpdate("COMMIT;");
    noAutoCommitConnection.commit();

    Element modulesElem = new Element("modules");

    for (ModuleEntry entry : getAppositeModules().values()) {
      modulesElem.appendChild(entry.toElement());
    }

    for (ModuleEntry entry : tc.developerModules.values()) {
      modulesElem.appendChild(entry.toElement());
    }

    Serializer serializer = new Serializer(new FileOutputStream(new File(installDirectory, "etc/modules.xml")));
    serializer.setIndent(4);
    serializer.write(new Document(modulesElem));

    StringBuilder bootLoaderSystem = new StringBuilder();
    bootLoaderSystem.append("#These file paths relative to the [install] determine classes to boot kernel\n\n");

    ModuleEntry bootJar = null;
    for (ModuleEntry entry : getAppositeModulesForRunLevel(0)) {
      bootLoaderSystem.append(entry.getPath() + "\n");

      if (entry.getIdentity().equals("urn:com:ten60:core:boot")) {
        bootJar = entry;
      }
    }

    FileUtils.writeStringToFile(new File(installDirectory, "etc/bootloader.conf"), bootLoaderSystem.toString());

    FileUtils.writeStringToFile(new File(installDirectory, "bin/bootjar.cnf"), bootJar.getPath().replace("lib/", ""));

    StringBuilder stemSystem = new StringBuilder();
    stemSystem.append("#Stem System Modules\n" +
                      "#These file paths relative to the [install]/boot/ determine modules to be loaded by kernel\n\n");

    for (ModuleEntry entry : getAppositeModulesForRunLevel(1)) {
      stemSystem.append(entry.getPath() + "\n");
    }

    FileUtils.writeStringToFile(new File(installDirectory, "etc/stem-system.conf"), stemSystem.toString());
  }

  public void updateAll() throws Exception {
    TransactionContext tc = new TransactionContext();
    startAppositeTransaction(tc);
    for (Package aPackage : packages) {
      if (aPackage.updateAvailable()) {
        System.out.println("Updating " + aPackage.getName() + " (installed: " + aPackage.getLatestInstalledVersion().getVersionString() +
                                                              " latest: " + aPackage.getVersions().last().getVersionString() + ")");
        aPackage.update(noAutoCommitConnection, installDirectory);
      }
    }
    endAppositeTransaction(tc);
  }

  private class TransactionContext {
    Map<String, ModuleEntry> existingModules;
    public Map<String, ModuleEntry> developerModules;
  }
}
