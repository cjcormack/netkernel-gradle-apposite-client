package org.netkernelroc.gradle;

import groovy.lang.Closure;
import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.QName;
import nu.xom.ParsingException;
import org.gradle.api.Project;
import org.netkernelroc.gradle.model.*;

import java.io.*;
import java.net.URL;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import nu.xom.Builder;
import nu.xom.Document;

import org.netkernelroc.gradle.apposite.AppositeManager;

public class NetKernelConvention {
  private String netKernelDownloadCacheStore = ".nk-download-cache";
  private String netKernelDownloadUrl = "http://ftp.heanet.ie/mirrors/download.1060.org/dist/1060-NetKernel-SE/1060-NetKernel-SE-5.1.1.jar";
  private String packageDownloadStore = "nk-download";
  private String netKernelInstallPath = "nk-instance";
  private AppositeStore appositeStore = new AppositeStore();
  private Set<AppositePackage> packages = new HashSet<AppositePackage>();
  private Project project;
  private AppositeManager appositeManager;

  public NetKernelConvention(Project project) throws SQLException {
    this.project = project;
  }

  public void nkRepositories(Closure closure) {
    closure.setDelegate(this);
    closure.call();
  }

	public void defineRepository(Map<String, String> map) {
    if(map.get("baseUrl") != null && map.get("path") != null && map.get("set") != null) {
      appositeStore.addRepository(new Repository(appositeStore, map.get("baseUrl"), map.get("path"), map.get("set")));
    } else {
      System.out.println("WARNING: Specified repository: map must include 'baseUrl', 'path' and 'set' attributes.");
    }
	}

  public void definePackage(Map<String, String> map) {
    AppositePackage aPackage = appositeStore.getAllPackages().get(map.get("name"));
    packages.add(aPackage);
    packages.addAll(aPackage.getVersions().last().allDependencies());
  }

  public void downloadPackages() throws Exception {
    File packageDownloadStoreFile = new File(packageDownloadStore, "packages");
    File packageExpandedStoreFile = new File(packageDownloadStore, "expanded-packages");
    File moduleDownloadStoreFile = new File(packageDownloadStore, "modules");
    if (!packageDownloadStoreFile.exists()) {
      packageDownloadStoreFile.mkdirs();
    }
    if (!packageExpandedStoreFile.exists()) {
      packageExpandedStoreFile.mkdirs();
    }
    if (!moduleDownloadStoreFile.exists()) {
      moduleDownloadStoreFile.mkdirs();
    }

    for (AppositePackage dependentPackage : packages) {
      System.out.println("Downloading " + dependentPackage.getVersions().last().getDownloadUrl());

      File packageFile = new File(packageDownloadStoreFile, dependentPackage.getName() + ".nkp.jar");

      downloadFile(new URL(dependentPackage.getVersions().last().getDownloadUrl()), packageFile);

      File expandedPackageFolder = new File(packageExpandedStoreFile, dependentPackage.getName());
      if (!expandedPackageFolder.exists()) {
        expandedPackageFolder.mkdirs();
      }

      expandZip(packageFile, expandedPackageFolder);

      File packageManifest = new File(expandedPackageFolder, "manifest.xml");
      XmlParser parser = new XmlParser();
      for (Object moduleNodeObj : parser.parse(packageManifest).getAt(new QName("module"))) {
        if (moduleNodeObj instanceof Node) {
          Node moduleNode = (Node) moduleNodeObj;
          String moduleUri = ((Node) moduleNode.getAt(new QName("uri")).get(0)).text();
          String moduleVersion = ((Node) moduleNode.getAt(new QName("version")).get(0)).text();
          String moduleLocation = ((Node) moduleNode.getAt(new QName("source")).get(0)).text();

          System.out.println("Extracting module " + moduleUri + " (" + moduleVersion + ")");
          File moduleFile = new File(expandedPackageFolder, moduleLocation);
          File targetFile = new File(moduleDownloadStoreFile, moduleFile.getName().replaceAll("-" + moduleVersion, ""));
          copyStream(new FileInputStream(moduleFile), new FileOutputStream(targetFile));
        }
      }
    }
  }

  public static void expandZip(File packageFile, File expandedPackageFolder) throws Exception {
    OutputStream out = null;
    ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(packageFile)));
    ZipEntry entry;
    while ((entry = in.getNextEntry()) != null) {
      if (entry.isDirectory()) {
        File targetFile= new File(expandedPackageFolder.getPath() + "/" + entry.getName());
        targetFile.mkdirs();
      } else {

        // write the files to the disk
        File targetFile= new File(expandedPackageFolder.getPath() + "/" + entry.getName());
        if (!targetFile.getParentFile().exists()) {
          targetFile.getParentFile().mkdirs();
        }
        out = new FileOutputStream(targetFile);
        copyStream(in, out);
      }
    }
    in.close();
  }

  public Set<AppositePackage> getPackages() {
       return Collections.unmodifiableSet(packages);
     }

  public void setPackages(Set<AppositePackage> packages) {
    this.packages = packages;
  }

  public static void copyStream(InputStream input, OutputStream output)  throws IOException {
    byte[] buffer = new byte[1024]; // Adjust if you want
    int bytesRead;
    while ((bytesRead = input.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
    }
  }

  public void downloadNetKernelSE() throws IOException {
    File netKernelDownloadCacheStoreFile = new File(netKernelDownloadCacheStore);
    if (!netKernelDownloadCacheStoreFile.exists()) {
      netKernelDownloadCacheStoreFile.mkdirs();
    }

    downloadFile(new URL(netKernelDownloadUrl), new File(netKernelDownloadCacheStoreFile, "1060-NetKernel-SE-5.1.1.jar"));
  }

  public void installNetKernelSE() throws Exception {
    File nkJar = new File(netKernelDownloadCacheStore, "1060-NetKernel-SE-5.1.1.jar");
    NetKernelInstaller.installNetKernel(nkJar, new File(netKernelInstallPath));
    if (new File(netKernelInstallPath, "etc/apposite/packagesDB.h2.db").exists()) {
      appositeManager = new AppositeManager(new File(netKernelInstallPath));
    }
  }

  public static void downloadFile(URL downloadUrl, File destinationFile) throws IOException {
    downloadFile(downloadUrl, destinationFile, false);
  }

  public static void downloadFile(URL downloadUrl, File destinationFile, boolean alwaysDownload) throws IOException {
    File tempDownloadFile = File.createTempFile("nk-download", ".jar");
    tempDownloadFile.deleteOnExit();

    if (destinationFile.exists() && !alwaysDownload) {
      System.out.println("Not downloading " + destinationFile.getName() + " as it already exists");
    } else {
      System.out.println("Downloading " + destinationFile + " from " + downloadUrl);
      InputStream downloadStream = downloadUrl.openStream();
      OutputStream targetStream = new FileOutputStream(tempDownloadFile);
      copyStream(downloadStream, targetStream);
      if (!destinationFile.getParentFile().exists()) {
        destinationFile.getParentFile().mkdirs();
      }
      tempDownloadFile.renameTo(destinationFile);
    }
  }

  public static Document downloadToXmlDocument(URL downloadUrl) throws IOException, ParsingException {
    Builder b = new Builder();
    return b.build(downloadUrl.openStream());
  }

  public AppositeManager getAppositeManager() {
    return appositeManager;
  }

  public static File createTempDir() {
    // this has been taken from Google's common-io (Apache license)
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    String baseName = System.currentTimeMillis() + "-";

    for (int counter = 0; counter < 10000; counter++) {
      File tempDir = new File(baseDir, baseName + counter);
      if (tempDir.mkdir()) {
        return tempDir;
      }
    }
    throw new IllegalStateException("Failed to create directory within "
        + 10000 + " attempts (tried "
        + baseName + "0 to " + baseName + (10000 - 1) + ')');
  }
}
