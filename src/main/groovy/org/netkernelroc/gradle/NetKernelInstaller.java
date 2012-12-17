package org.netkernelroc.gradle;

import java.io.*;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class NetKernelInstaller {
  public static void installNetKernel(File netKernelJar, File targetDir) throws Exception {
    boolean expand = true; // Apposite doesn't work with expand=true yet
    boolean source = false;

		String proxyHost = "";
		String proxyPort = "";

    if (!targetDir.exists()) {
      targetDir.mkdirs();
      System.out.println("Installing NetKernel to " + targetDir.getAbsolutePath());
    } else {
      System.out.println("NetKernel has already been installed");
      return;
    }

    //start install
    JarFile jarFile = new JarFile(netKernelJar);

    //setup lib directory
    final String libPrefix = "lib";
    File lib = new File(targetDir,libPrefix);
    lib.mkdir();
    new File(lib, "ext").mkdir();

    List<File> bootFiles = new ArrayList<File>();
    File bootloaderJar = null;

    Enumeration<JarEntry> e = jarFile.entries();
    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      if (!entry.isDirectory()) {
        String name = entry.getName();
        if (name.startsWith(libPrefix)  && (name.endsWith(".jar") || name.endsWith(".zip"))) {
          InputStream is = jarFile.getInputStream(entry);
          File bootFile = installJar(is, name, targetDir, false, source);
          if (bootFile!=null) {
            bootFiles.add(bootFile);
          }
          if (name.indexOf("core.boot") >= 0) {
            bootloaderJar = bootFile;
          }
        }
      }
    }

    //setup modules directory
    final String modulesPrefix = "modules";
    new File(targetDir, modulesPrefix).mkdir();

    List<File> moduleFiles = new ArrayList<File>();
    e = jarFile.entries();
    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      if (!entry.isDirectory()) {
        String name = entry.getName();
        if (name.startsWith(modulesPrefix) && (name.endsWith(".jar") || name.endsWith(".sjar") || name.endsWith(".zip"))) {
          InputStream is = jarFile.getInputStream(entry);
          boolean lexpand = expand;
          if(name.endsWith(".sjar")) {
            lexpand = false;
          }
          File moduleFile = installJar(is, name, targetDir, lexpand, source);
          if (moduleFile != null) {
            moduleFiles.add(moduleFile);
          }
        }
      }
    }

    //setup etc directory
    File etcDir = new File(targetDir,"etc");
    etcDir.mkdir();

    //setup etc/license directory
    new File(etcDir, "license").mkdir();

    //setup log directory
    new File(targetDir,"log").mkdir();

    //setup etc directory
    File appositeDir = new File(targetDir,"etc/apposite");
    appositeDir.mkdir();
    URI targetURI = targetDir.toURI();

    //Write apposite database.
    final String appositeDBPrefix = "etc/apposite/packagesDB";
    e = jarFile.entries();
    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      String name = entry.getName();
      if (name.startsWith(appositeDBPrefix)) {
        File targetFile=new File(targetDir, name);
        if (entry.isDirectory()) {
          targetFile.mkdir();
        } else {
          InputStream is = jarFile.getInputStream(entry);
          NetKernelConvention.copyStream(is, new FileOutputStream(targetFile));
        }
      }
    }

    if (expand) {
      String sql ="UPDATE MODULES SET LOCALSRC=REPLACE(LOCALSRC, '.jar', '/' ) WHERE RUNLEVEL>0 AND LOCALSRC NOT LIKE '%.sjar' ;";
      File dbpath = new File(targetDir, "etc/apposite/packagesDB");

      Connection conn = DriverManager.getConnection("jdbc:h2:"+dbpath.getAbsolutePath()+";WRITE_DELAY=0");

      Statement stmt = conn.createStatement();
      stmt.executeUpdate(sql);

      conn.close();
    }

    //write bootloader.xml
    File bootloader = new File(etcDir, "bootloader.conf");
    Writer w = new OutputStreamWriter(new FileOutputStream(bootloader));
    w.write("# these file paths relative to the [install] determine classloader to boot kernel\n\n");
    for (File f : bootFiles) {
      URI fu = targetURI.relativize(f.toURI());
      w.write(fu.toString());
      w.write('\n');
    }
    w.close();

    String s;
    //copy stem-system.conf
    if (expand) {
      s = readEntryAsString(jarFile, "etc/stem-system-expanded.conf");
    } else {
      s = readEntryAsString(jarFile, "etc/stem-system-jarred.conf");
    }
    writeStringToFile(s, new File(etcDir, "stem-system.conf"));

    //write modules.xml
    if (expand) {
      s = readEntryAsString(jarFile, "etc/modules-expanded.xml");
    } else {
      s = readEntryAsString(jarFile, "etc/modules-jarred.xml");
    }
    writeStringToFile(s, new File(etcDir, "modules.xml"));


    //copy kernel.properties
    File expandedDir = new File(targetDir, "lib/expanded/");
    expandedDir.mkdir();
    s = readEntryAsString(jarFile, "etc/kernel.properties");
    String path = expandedDir.getAbsolutePath();
    path = path.replaceAll("\\\\", "\\\\\\\\\\\\\\\\"); //escape backslash for both regex and property files
    s = s.replaceAll("netkernel.layer0.expandDir=", "netkernel.layer0.expandDir=" + path);
    if (proxyHost.length() > 0) {
      s = s.replaceAll("%HTTPPROXYHOST%", proxyHost);
    }
    if (proxyHost.length() > 0) {
      s = s.replaceAll("%HTTPPROXYPORT%", proxyPort);
    }
    writeStringToFile(s, new File(etcDir, "kernel.properties"));

    //Write log config
    s = readEntryAsString(jarFile, "etc/KernelLogConfig.xml");
    writeStringToFile(s, new File(etcDir, "KernelLogConfig.xml"));

    //Write log-level file out
    s = readEntryAsString(jarFile, "etc/logLevels.xml");
    writeStringToFile(s, new File(etcDir, "logLevels.xml"));


    File binDir = new File(targetDir, "bin");
    binDir.mkdir();

    //Write bootjar.cnf
    s = readEntryAsString(jarFile, "bin/bootjar.cnf");
    s = s.replaceAll("%BOOTLOADER%", bootloaderJar.getName());
    File scriptTarget = new File(binDir, "bootjar.cnf");
    writeStringToFile(s, scriptTarget);

    //Write jvmsettings.cnf
    s = readEntryAsString(jarFile, "bin/jvmsettings.cnf");
    scriptTarget = new File(binDir, "jvmsettings.cnf");
    writeStringToFile(s, scriptTarget);

    if (File.separatorChar == '/') {  // bin/netkernel.sh
      path = targetDir.getAbsolutePath();
      s = readEntryAsString(jarFile, "bin/netkernel.sh");
      s = s.replaceAll("%INSTALL%", path);
      scriptTarget = new File(binDir, "netkernel.sh");
      writeStringToFile(s, scriptTarget);

      Runtime.getRuntime().exec(new String[]{"chmod", "u+x", scriptTarget.getAbsolutePath()});
    } else {  // bin/netkernel.bat
      s = readEntryAsString(jarFile, "bin/netkernel.bat");
      path = targetDir.getAbsolutePath();
      path = path.replaceAll("\\\\", "\\\\\\\\");
      s = s.replaceAll("%INSTALL%", path);
      writeStringToFile(s, new File(binDir, "netkernel.bat"));

      // bin/netkernel-debug.bat
      s = readEntryAsString(jarFile, "bin/netkernel-debug.bat");
      s = s.replaceAll("%INSTALL%", path);
      writeStringToFile(s, new File(binDir, "netkernel-debug.bat"));
    }
  }

  private static String readEntryAsString(JarFile aJarFile, String aName) throws IOException {
    InputStream is = aJarFile.getInputStream(aJarFile.getEntry(aName));
    InputStreamReader sr = new InputStreamReader(is);
    StringWriter sw = new StringWriter(1024);
    int c;
    char[] cbuf = new char[256];
    while ((c = sr.read(cbuf)) > 0) {
      sw.write(cbuf, 0, c);
    }
    return sw.toString();
  }

  private static void writeStringToFile(String aString, File aFile) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(aString.getBytes());
    FileOutputStream fos = new FileOutputStream(aFile);
    NetKernelConvention.copyStream(bais, fos);
  }

  private static File installJar(InputStream aSource, String aName, File aTargetDir, boolean aExpand, boolean aIncludeSource) throws Exception {
    File result;
    final String SRC_TAG = "-src";
    boolean isSource=aName.contains(SRC_TAG);
    if (isSource && !aIncludeSource) return null;

    File targetFile = new File(aTargetDir,aName);
    NetKernelConvention.copyStream(aSource, new FileOutputStream(targetFile));

    if (aExpand) {
      File expandDir;
      if (isSource) {
        int i = aName.indexOf(SRC_TAG);
        String name = aName.substring(0, i) + aName.substring(i + SRC_TAG.length(), aName.length() - 4);
        expandDir = new File(aTargetDir, name);
      } else {
        expandDir = new File(aTargetDir, aName.substring(0, aName.length() - 4));
      }

      expandDir.mkdir();
      JarFile jarFile = new JarFile(targetFile);
      Enumeration e = jarFile.entries();
      while (e.hasMoreElements()) {
        JarEntry entry = (JarEntry) e.nextElement();
        if (entry.isDirectory()) continue;
        String name = entry.getName();
        if (name.startsWith("META-INF")) continue;
        InputStream is = jarFile.getInputStream(entry);
        File dest = new File(expandDir, name);
        dest.getParentFile().mkdirs();
        NetKernelConvention.copyStream(is, new FileOutputStream(dest));
      }
      jarFile.close();
      targetFile.delete();
      result = expandDir;
    } else {
      result = targetFile;
    }
    if (isSource) result = null;
    return result;
  }
}
