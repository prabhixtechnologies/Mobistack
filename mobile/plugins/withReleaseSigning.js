const { withAppBuildGradle } = require("@expo/config-plugins");

const LOAD_PROPS = `
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}
`;

const RELEASE_SIGNING = `
        release {
            if (keystorePropertiesFile.exists()) {
                keyAlias keystoreProperties['keyAlias']
                keyPassword keystoreProperties['keyPassword']
                storeFile file(keystoreProperties['storeFile'])
                storePassword keystoreProperties['storePassword']
            }
        }`;

function withReleaseSigning(config) {
  return withAppBuildGradle(config, (mod) => {
    let src = mod.modResults.contents;
    if (src.includes("keystore.properties")) {
      return mod;
    }
    src = src.replace("android {", `${LOAD_PROPS}\nandroid {`);
    src = src.replace(
      /signingConfigs \{\s*debug \{[\s\S]*?\n        \}\n    \}/,
      (block) => block.replace("\n    }", `${RELEASE_SIGNING}\n    }`),
    );
    src = src.replace(
      /release \{\s*\n\s*\/\/ Caution![\s\S]*?signingConfig signingConfigs\.debug/,
      "release {\n            signingConfig keystorePropertiesFile.exists() ? signingConfigs.release : signingConfigs.debug",
    );
    mod.modResults.contents = src;
    return mod;
  });
}

module.exports = withReleaseSigning;
