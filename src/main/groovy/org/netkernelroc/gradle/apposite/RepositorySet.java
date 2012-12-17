package org.netkernelroc.gradle.apposite;

import nu.xom.Document;
import nu.xom.Node;
import nu.xom.Nodes;
import org.netkernelroc.gradle.NetKernelConvention;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RepositorySet {
  private RepositoryCollection collection;
  private long id;
  private String name;

  public RepositorySet(RepositoryCollection collection, long id, String name) {
    this.collection = collection;
    this.id = id;
    this.name = name;
  }

  public RepositoryCollection getCollection() {
    return collection;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name + "(#" +id + ")";
  }

  public void synchronize(Connection connection) throws Exception {
    String basePath = collection.getBasePath();

    final String addPackageSql = "MERGE INTO PACKAGES(\n" +
                                 "                REPOCOLLECTIONSETID,\n" +
                                 "                NAME,\n" +
                                 "                DESCR,\n" +
                                 "                MAINTAINER,\n" +
                                 "                RUNLEVEL,\n" +
                                 "                SECTION,\n" +
                                 "                WWW,\n" +
                                 "                LICENSE\n" +
                                 "           )\n" +
                                 " KEY       (REPOCOLLECTIONSETID, NAME)\n" +
                                 " VALUES    (\n" +
                                 "                ?," +
                                 "                ?," +
                                 "                ?," +
                                 "                ?," +
                                 "                ?," +
                                 "                ?," +
                                 "                ?," +
                                 "                ?" +
                                 "           );";
    final PreparedStatement addPackagePS = connection.prepareStatement(addPackageSql);

    final String addPackageVersionSql = "MERGE INTO PACKAGE_VERSIONS(\n" +
                                        "               PACKAGEID,\n" +
                                        "               VERSION,\n" +
                                        "               SIZE,\n" +
                                        "               INSTALLED,\n" +
                                        "               TYPE,\n" +
                                        "               FILENAME,\n" +
                                        "               FILEPATH,\n" +
                                        "               LOCALSRC,\n" +
                                        "               DESCR,\n" +
                                        "               SIGNATURE,\n" +
                                        "               MD5,\n" +
                                        "               SHA256\n" +
                                        "           )\n" +
                                        "KEY        (PACKAGEID, VERSION)\n" +
                                        "VALUES     (\n" +
                                        "            ( SELECT ID FROM PACKAGES\n" +
                                        "              WHERE  NAME=?\n" +
                                        "              AND    REPOCOLLECTIONSETID=?\n" +
                                        "            ),\n" +
                                        "            ?,\n" +
                                        "            ?,\n" +
                                        "            ( SELECT INSTALLED\n" +
                                        "              FROM   PACKAGES, PACKAGE_VERSIONS\n" +
                                        "              WHERE  PACKAGES.NAME=?\n" +
                                        "              AND    PACKAGES.REPOCOLLECTIONSETID=?\n" +
                                        "              AND    PACKAGES.ID=PACKAGE_VERSIONS.PACKAGEID\n" +
                                        "              AND    PACKAGE_VERSIONS.VERSION=?\n" +
                                        "            ),\n" +
                                        "            ?,\n" +
                                        "            ?,\n" +
                                        "            ?,\n" +
                                        "            NULL,\n" +
                                        "            ?,\n" +
                                        "            ?,\n" +
                                        "            ?,\n" +
                                        "            ?\n" +
                                        "           );";
    final PreparedStatement addPackageVersionPS = connection.prepareStatement(addPackageVersionSql);

    final String deleteDependenciesSql = "DELETE\n" +
                                         "FROM   PACKAGE_VERSION_DEPENDS\n" +
                                         "WHERE  PACKAGEVID=( SELECT PACKAGE_VERSIONS.ID\n" +
                                         "                    FROM   PACKAGE_VERSIONS,\n" +
                                         "                            PACKAGES\n" +
                                         "                    WHERE   PACKAGE_VERSIONS.PACKAGEID=PACKAGES.ID\n" +
                                         "                    AND     PACKAGE_VERSIONS.VERSION=?\n" +
                                         "                    AND     PACKAGES.NAME=?\n" +
                                         "                  );";
    final PreparedStatement deleteDependenciesPS = connection.prepareStatement(deleteDependenciesSql);

    final String addDependencySql = "INSERT INTO PACKAGE_VERSION_DEPENDS\n" +
                                    "VALUES ( NULL,\n" +
                                    "         ( SELECT PACKAGE_VERSIONS.ID\n" +
                                    "           FROM   PACKAGE_VERSIONS,\n" +
                                    "                  PACKAGES\n" +
                                    "           WHERE  PACKAGE_VERSIONS.PACKAGEID=PACKAGES.ID\n" +
                                    "           AND    PACKAGE_VERSIONS.VERSION=?\n" +
                                    "           AND    PACKAGES.NAME=?\n" +
                                    "         ),\n" +
                                    "         ?,\n" +
                                    "         ?,\n" +
                                    "         ?,\n" +
                                    "         ?);";
    final PreparedStatement addDependencyPS = connection.prepareStatement(addDependencySql);

    Document hashesXml = NetKernelConvention.downloadToXmlDocument(new URL(basePath + "hashes.xml"));
    //NetKernelConvention.downloadToXmlDocument(new URL(basePath + "hashes.sig"));

    for (RepositoryType type : RepositoryType.values()) {
      String setBasePath = basePath + type.getName() + "/" + name;

      Document repositoryXml = NetKernelConvention.downloadToXmlDocument(new URL(setBasePath + "/repository.xml"));
      Nodes packages = repositoryXml.query("/packages/package");

      for (int i = 0; i < packages.size(); i++) {
        Node aPackage = packages.get(i);

        String name = aPackage.query("name").get(0).getValue();
        String packageDesc = aPackage.query("packagedescr").get(0).getValue();
        String maintainer = aPackage.query("maintainer").get(0).getValue();
        int runLevel = Integer.parseInt(aPackage.query("runlevel").get(0).getValue());
        String section = aPackage.query("section").get(0).getValue();
        String www = aPackage.query("www").get(0).getValue();
        String license = aPackage.query("license").get(0).getValue();
        int size = Integer.parseInt(aPackage.query("size").get(0).getValue());
        String fileName = aPackage.query("filename").get(0).getValue();
        String filePath = aPackage.query("filepath").get(0).getValue();
        String versionDesc = aPackage.query("versiondescr").get(0).getValue();
        String trustSignature = aPackage.query("trust/signature").get(0).getValue();
        String trustMd5 = aPackage.query("trust/md5").get(0).getValue();
        String trustSha256 = aPackage.query("trust/sha256").get(0).getValue();
        String[] versionArrayStr = aPackage.query("version").get(0).getValue().split("\\.");

        Integer[] versionArray = stringArrayToIntArray(versionArrayStr);

        addPackagePS.clearParameters();
        addPackagePS.setLong(1, id);
        addPackagePS.setString(2, name);
        addPackagePS.setString(3, packageDesc);
        addPackagePS.setString(4, maintainer);
        addPackagePS.setInt(5, runLevel);
        addPackagePS.setString(6, section);
        addPackagePS.setString(7, www);
        addPackagePS.setString(8, license);
        addPackagePS.executeUpdate();

        addPackageVersionPS.clearParameters();
        addPackageVersionPS.setString(1, name);
        addPackageVersionPS.setLong(2, id);
        addPackageVersionPS.setObject(3, versionArray);
        addPackageVersionPS.setInt(4, size);
        addPackageVersionPS.setString(5, name);
        addPackageVersionPS.setLong(6, id);
        addPackageVersionPS.setObject(7, versionArray);
        addPackageVersionPS.setInt(8, type.getId());
        addPackageVersionPS.setString(9, fileName);
        addPackageVersionPS.setString(10, filePath);
        addPackageVersionPS.setString(11, versionDesc);
        addPackageVersionPS.setString(12, trustSignature);
        addPackageVersionPS.setString(13, trustMd5);
        addPackageVersionPS.setString(14, trustSha256);
        addPackageVersionPS.executeUpdate();

        Nodes dependencies = aPackage.query("dependencies/dependency");

        deleteDependenciesPS.clearParameters();
        deleteDependenciesPS.setObject(1, versionArrayStr);
        deleteDependenciesPS.setString(2, name);
        deleteDependenciesPS.executeUpdate();

        for (int dependenciesI = 0; dependenciesI < dependencies.size(); dependenciesI++) {
          Node dependency = dependencies.get(dependenciesI);

          addDependencyPS.clearParameters();
          addDependencyPS.setObject(1, versionArray);
          addDependencyPS.setString(2, name);
          addDependencyPS.setString(3, dependency.query("name").get(0).getValue());
          addDependencyPS.setInt(4, DependencyType.typeForValue(dependency.query("deptype").get(0).getValue()).getId());
          addDependencyPS.setInt(5, DependencyEquality.equalityForValue(dependency.query("equality").get(0).getValue()).getId());
          addDependencyPS.setObject(6, stringArrayToIntArray(aPackage.query("version").get(0).getValue().split("\\.")));
          addDependencyPS.addBatch();
        }
        addDependencyPS.executeBatch();
      }
    }
  }

  public static Integer[] stringArrayToIntArray(String[] versionArrayStr) {
    Integer[] versionArray;
    versionArray = new Integer[versionArrayStr.length];
    for (int vAI = 0; vAI < versionArrayStr.length; vAI++) {
      versionArray[vAI] = Integer.parseInt(versionArrayStr[vAI]);
    }
    return versionArray;
  }

  public List<Package> loadPackages(Connection noAutoCommitConnection) throws SQLException {
    List<Package> packages = new ArrayList<Package>();

    final String packagesSql = "SELECT  ID,\n" +
                               "        NAME,\n" +
                               "        DESCR,\n" +
                               "        MAINTAINER,\n" +
                               "        RUNLEVEL,\n" +
                               "        SECTION,\n" +
                               "        WWW,\n" +
                               "        LICENSE\n" +
                               "FROM    PACKAGES\n" +
                               "WHERE   REPOCOLLECTIONSETID=?;";
    final PreparedStatement packagesPS = noAutoCommitConnection.prepareStatement(packagesSql);

    final String packageVersionsSql = "SELECT ID,\n" +
                                      "       VERSION[0] AS MAJOR,\n" +
                                      "       VERSION[1] AS MINOR,\n" +
                                      "       VERSION[2] AS PATCH,\n" +
                                      "       SIZE,\n" +
                                      "       INSTALLED,\n" +
                                      "       TYPE,\n" +
                                      "       FILENAME,\n" +
                                      "       FILEPATH,\n" +
                                      "       LOCALSRC,\n" +
                                      "       DESCR,\n" +
                                      "       SIGNATURE,\n" +
                                      "       MD5,\n" +
                                      "       SHA256,\n" +
                                      "       HASUPDATE,\n" +
                                      "       HASSECURITY\n" +
                                      "FROM   PACKAGE_VERSIONS\n" +
                                      "WHERE  PACKAGEID=?;";
    final PreparedStatement packageVersionsPS = noAutoCommitConnection.prepareStatement(packageVersionsSql);

    packagesPS.setLong(1, id);
    ResultSet packagesRS = packagesPS.executeQuery();

    while (packagesRS.next()) {
      Package aPackage = new Package(this,
                                     packagesRS.getLong("id"),
                                     packagesRS.getString("name"),
                                     packagesRS.getString("descr"),
                                     packagesRS.getString("maintainer"),
                                     packagesRS.getInt("runlevel"),
                                     packagesRS.getString("section"),
                                     packagesRS.getString("www"),
                                     packagesRS.getString("license"));

      packageVersionsPS.clearParameters();
      packageVersionsPS.setLong(1, aPackage.getId());
      ResultSet packageVersionsRS = packageVersionsPS.executeQuery();

      while (packageVersionsRS.next()) {
        PackageVersion version = new PackageVersion(aPackage,
                                                    packageVersionsRS.getLong("id"),
                                                    packageVersionsRS.getInt("major"),
                                                    packageVersionsRS.getInt("minor"),
                                                    packageVersionsRS.getInt("patch"),
                                                    packageVersionsRS.getInt("size"),
                                                    packageVersionsRS.getBoolean("installed"),
                                                    RepositoryType.typeById(packageVersionsRS.getInt("type")),
                                                    packageVersionsRS.getString("filename"),
                                                    packageVersionsRS.getString("filepath"),
                                                    packageVersionsRS.getString("localsrc"),
                                                    packageVersionsRS.getString("descr"),
                                                    packageVersionsRS.getString("signature"),
                                                    packageVersionsRS.getString("md5"),
                                                    packageVersionsRS.getString("sha256"),
                                                    packageVersionsRS.getBoolean("hasupdate"),
                                                    packageVersionsRS.getBoolean("hassecurity"));
        aPackage.addVersion(version);
      }

      PackageVersion latestInstalledVersion = null;
      for (PackageVersion version : aPackage.getVersions()) {
        if (version.isInstalled()) {
           latestInstalledVersion = version;
        }
      }

      aPackage.setLatestInstalledVersion(latestInstalledVersion);

      packages.add(aPackage);
    }

    return packages;
  }
}
