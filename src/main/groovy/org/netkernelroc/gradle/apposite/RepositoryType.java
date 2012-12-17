package org.netkernelroc.gradle.apposite;

public enum RepositoryType {
  BASE(1, "base"),
  UPDATE(2, "update"),
  SECURITY(3, "security");

  private int id;
  private String name;

  private RepositoryType(int id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public int getId() {
    return id;
  }

  public static RepositoryType typeById(int typeId) {
    for (RepositoryType type : values()) {
      if (type.id == typeId) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unrecognised repository type id '" + typeId + "' in Apposite DB");
  }
}
