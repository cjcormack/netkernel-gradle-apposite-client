package org.netkernelroc.gradle.apposite;

import org.netkernelroc.gradle.NetKernelConvention;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PackageVersion implements Comparable<PackageVersion> {
  private Package aPackage;
  private RepositoryType versionType;
  private int major;
  private int minor;
  private int patch;
  private String filePath;
  private String fileName;
  private long id;
  private int size;
  private boolean installed;
  private String localSrc;
  private String description;
  private String signature;
  private String md5;
  private String sha256;
  private boolean hasUpdate;
  private boolean hasSecurity;

  //private List<Dependency> dependencies = new ArrayList<Dependency>();

  public PackageVersion(Package aPackage,long id, int major, int minor, int patch, int size, boolean installed, RepositoryType versionType,
                        String fileName, String filePath, String localSrc, String description, String signature, String md5,
                        String sha256, boolean hasUpdate, boolean hasSecurity) {
    this.aPackage = aPackage;
    this.id = id;
    this.size = size;
    this.installed = installed;
    this.versionType = versionType;
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.fileName = fileName;
    this.filePath = filePath;
    this.localSrc = localSrc;
    this.description = description;
    this.signature = signature;
    this.md5 = md5;
    this.sha256 = sha256;
    this.hasUpdate = hasUpdate;
    this.hasSecurity = hasSecurity;

  }

  public Package getPackage() {
    return aPackage;
  }

  public RepositoryType getVersionType() {
    return versionType;
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  public int getPatch() {
    return patch;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getFileName() {
    return fileName;
  }

  public long getId() {
    return id;
  }

  public int getSize() {
    return size;
  }

  public boolean isInstalled() {
    return installed;
  }

  public String getLocalSrc() {
    return localSrc;
  }

  public String getDescription() {
    return description;
  }

  public String getSignature() {
    return signature;
  }

  public String getMd5() {
    return md5;
  }

  public String getSha256() {
    return sha256;
  }

  public boolean hasUpdate() {
    return hasUpdate;
  }

  public boolean hasSecurity() {
    return hasSecurity;
  }

  public String getDownloadUrl() {
    return aPackage.getSet().getCollection().getRepository().getUrlBase() + "/" + filePath + fileName;
  }

/*public boolean addDependency(Dependency dependency) {
    return dependencies.add(dependency);
  }

  public List<Dependency> getDependencies() {
    return Collections.unmodifiableList(dependencies);
  }

  public void setDependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
  }

  public Set<AppositePackage> allDependencies() {
    Set<AppositePackage> allDependencies = new HashSet<AppositePackage>();

    AppositeStore appositeStore = aPackage.getRepository().getAppositeStore();

    for (Dependency dependency : dependencies) {
      AppositePackage dependentPackage = appositeStore.getAllPackages().get(dependency.getName());
      if (dependentPackage == null) {
        System.err.println("WARNING: Dependent package '" + dependency.getName() + "' does not exist in any of the repositories that have been supplied.");
      } else {
        allDependencies.add(dependentPackage);
        allDependencies.addAll(dependentPackage.getVersions().last().allDependencies());
      }
    }

    return allDependencies;
  }*/

  @Override
  public int compareTo(PackageVersion otherVersion) {
    if (this.major == otherVersion.major && this.minor == otherVersion.minor && otherVersion.patch == this.patch) {
      return 0;
    } else {
      if ((this.major > otherVersion.major) ||
          (this.major == otherVersion.major && this.minor > otherVersion.minor) ||
          (this.major == otherVersion.major && this.minor == otherVersion.minor && this.patch > otherVersion.patch)) {
        return 1;
      } else {
        return -1;
      }
    }
  }

  public String getVersionString() {
    return major + "." + minor + "." + patch;
  }

  @Override
  public String toString() {
    return getVersionString() + " [" + versionType + "]";
  }

  public File download(File nkInstance, Connection connection) throws IOException, SQLException {
    final String localSrcSql = "UPDATE PACKAGE_VERSIONS SET LOCALSRC=? WHERE ID=?;";
    final PreparedStatement localSrcPS = connection.prepareStatement(localSrcSql);

    File downloadFile = null;

    if (localSrc != null) {
      File localSrcFile = new File(localSrc);
      if (localSrcFile.exists()) {
        downloadFile = new File(localSrc);
      }
    }

    if (downloadFile == null) {
      downloadFile = new File(nkInstance, "package-cache/" + fileName);
      URL downloadUrl = new URL(getDownloadUrl());
      NetKernelConvention.downloadFile(downloadUrl, downloadFile);
    }

    localSrcPS.clearParameters();
    localSrcPS.setString(1, downloadFile.getAbsolutePath());
    localSrcPS.setLong(2, id);
    localSrcPS.executeUpdate();
    localSrc = downloadFile.getAbsolutePath();

    System.out.println("localSrc: " + localSrc + " (" + id + ")");

    return downloadFile;
  }
}
