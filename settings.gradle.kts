pluginManagement {
    repositories {
        // 国内网络下优先走阿里云镜像，失败会自动回落到下面的官方仓库。
        // 如果同步时报奇怪的错误，把这三行 maven("https://maven.aliyun.com/...") 删掉即可。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
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
