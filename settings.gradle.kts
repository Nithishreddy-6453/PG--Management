pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google\\.android.*")
        includeGroupByRegex("com\\.google\\.firebase.*")
        includeGroupByRegex("com\\.google\\.gms.*")
        includeGroupByRegex("com\\.google\\.testing.*")
        includeGroupByRegex("com\\.google\\.protobuf.*")
        includeGroupByRegex("com\\.google\\.mlkit.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "PG Manager"

include(":app")
