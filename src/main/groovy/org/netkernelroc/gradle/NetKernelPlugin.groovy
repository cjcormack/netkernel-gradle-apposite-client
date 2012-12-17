package org.netkernelroc.gradle;

import org.gradle.api.Plugin
import org.gradle.api.Project

class NetKernelPlugin implements Plugin<Project> {

  @Override
  void apply(Project p) {
    p.convention.plugins.netkernel = new NetKernelConvention(p)
    p.task('nk-download-packages') << {
      p.convention.plugins.netkernel.downloadPackages();
    }
    p.task('nk-download-se') << {
      p.convention.plugins.netkernel.downloadNetKernelSE();
    }
    p.task(dependsOn: 'nk-download-se', 'nk-install-se') << {
      p.convention.plugins.netkernel.installNetKernelSE();
    }
    p.task(dependsOn: 'nk-install-se', 'nk-apposite-synchronize') << {
      p.convention.plugins.netkernel.getAppositeManager().synchronize();
    }
    p.task(dependsOn: 'nk-apposite-synchronize', 'nk-apposite-update-all') << {
      p.convention.plugins.netkernel.getAppositeManager().updateAll();
    }
  }
}
