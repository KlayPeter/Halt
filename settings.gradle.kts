pluginManagement {
    repositories {
        // 插件解析走官方仓库优先：阿里云镜像对某些插件（如 KSP）会返回异常响应，
        // 导致 CI 上解析失败。国内网络慢的话可以整体下移。
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "ScreenTime"
include(":app")
