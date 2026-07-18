const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

process.noDeprecation = true;

const rootDir = path.resolve(__dirname, "..");
const buildType = (process.env.KUIKLY_OHOS_BUILD_TYPE || "Debug").toLowerCase();
const taskBuildType = buildType === "release" ? "Release" : "Debug";
const gradleTask = `:shared:link${taskBuildType}SharedOhosArm64`;
const gradleCmd = process.platform === "win32"
  ? path.join(rootDir, "gradlew.bat")
  : path.join(rootDir, "gradlew");

function gradleEnv() {
  const env = { ...process.env };
  if (!env.JAVA_HOME && process.platform === "win32") {
    const candidates = [
      "C:\\Program Files\\Huawei\\DevEco Studio\\jbr",
      "C:\\Program Files\\Android\\Android Studio\\jbr",
    ];
    const javaHome = candidates.find((candidate) => fs.existsSync(path.join(candidate, "bin", "java.exe")));
    if (javaHome) {
      env.JAVA_HOME = javaHome;
      env.PATH = `${path.join(javaHome, "bin")};${env.PATH || ""}`;
      console.log(`[kuikly-ohos] JAVA_HOME was not set; using ${javaHome}`);
    }
  }
  return env;
}

function runGradle() {
  const gradleArgs = ["-c", "settings.ohos.gradle.kts", gradleTask];
  console.log(`[kuikly-ohos] Running ${gradleCmd} ${gradleArgs.join(" ")}`);
  const result = spawnSync(gradleCmd, gradleArgs, {
    cwd: rootDir,
    env: gradleEnv(),
    stdio: "inherit",
    shell: process.platform === "win32",
  });
  if (result.error) {
    console.error(result.error);
  }
  if (result.status !== 0) {
    process.exit(result.status || 1);
  }
}

function findFile(dir, fileName) {
  if (!fs.existsSync(dir)) {
    return null;
  }
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const entryPath = path.join(dir, entry.name);
    if (entry.isFile() && entry.name === fileName) {
      return entryPath;
    }
    if (entry.isDirectory()) {
      const found = findFile(entryPath, fileName);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

function copyFile(source, target) {
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(source, target);
  console.log(`[kuikly-ohos] Copied ${source} -> ${target}`);
}

runGradle();

const binDir = path.join(rootDir, "shared", "build", "bin", "ohosArm64");
const sharedLib = findFile(binDir, "libshared.so");
if (!sharedLib) {
  throw new Error(`[kuikly-ohos] libshared.so was not found under ${binDir}`);
}

copyFile(sharedLib, path.join(rootDir, "ohosApp", "entry", "libs", "arm64-v8a", "libshared.so"));

const header =
  findFile(path.dirname(sharedLib), "libshared_api.h") ||
  findFile(path.dirname(sharedLib), "shared_api.h") ||
  findFile(binDir, "libshared_api.h") ||
  findFile(binDir, "shared_api.h");

if (header) {
  copyFile(header, path.join(rootDir, "ohosApp", "entry", "src", "main", "cpp", "include", "libshared_api.h"));
} else {
  console.warn("[kuikly-ohos] libshared header was not found; keeping existing C++ include header.");
}
