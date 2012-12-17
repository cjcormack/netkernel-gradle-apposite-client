package org.netkernelroc.gradle.model;

import java.util.*;

public class AppositeStore {
  private Set<Repository> repositories = new HashSet<Repository>();
  private Map<String, AppositePackage> allPackages = new HashMap<String, AppositePackage>();

  public void addRepository(Repository repository) {
    repositories.add(repository);
    allPackages.putAll(repository.getPackages());
  }

  public Set<Repository> getRepositories() {
    return Collections.unmodifiableSet(repositories);
  }

  public Map<String, AppositePackage> getAllPackages() {
    return Collections.unmodifiableMap(allPackages);
  }
}
